using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Moderation;

namespace RoadPulse.Api.Modules.PlaceProblems;

public record PlaceProblemRequest(string PlaceId, PlaceProblemKind Kind, double? Lat, double? Lon, string? Note);
public record PlaceProblemDto(Guid Id, string PlaceId, string PlaceName, PlaceProblemKind Kind, string? Note,
    double? SuggestedLat, double? SuggestedLon, CommunityPlaceStatus Status);
/// <summary>For the moderator: the report, where the place is now (null when already hidden) and how far the driver wants it moved.</summary>
public record PlaceProblemReviewItem(PlaceProblemDto Problem, DateTimeOffset CreatedAt, Guid DeviceId, int DeviceProblems, int DeviceRejected,
    string? Address, double? CurrentLat, double? CurrentLon, double? MoveM);
/// <summary>Approving a wrong position: the moderator may give the right position (overrides the driver's).</summary>
public record PlaceProblemDecision(string Reason, double? Lat, double? Lon);

/// <summary>
/// "Report a problem with this place": drivers say a place from the Overture data has its pin in
/// the wrong spot, is closed for good, or is not there. A moderator checks each one (admin page,
/// "Place problems"); approving it moves or hides the place, and the fix is kept in
/// place_corrections so the monthly data import does not undo it.
/// </summary>
public static class PlaceProblemEndpoints
{
    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);
    public const int MaxPendingPerDevice = 20;
    /// <summary>A place can be moved at most this far by a report (further is a different place).</summary>
    public const double MaxMoveM = 50_000;

    public static RouteGroupBuilder MapPlaceProblemEndpoints(this RouteGroupBuilder v1)
    {
        v1.MapGroup("/places/problems").WithTags("Place problems").RequireAuthorization()
            .MapPost("", Report).RequireRateLimiting(RateLimits.Reports)
            .WithName("ReportPlaceProblem").WithSummary("Says a place in search is wrong (position, closed, not there). Applied after review.");

        var mod = v1.MapGroup("/moderation/place-problems").WithTags("Moderation")
            .RequireAuthorization(ModeratorAuthHandler.Policy).RequireRateLimiting(RateLimits.Moderation);
        mod.MapGet("", ReviewList).WithName("PlaceProblemsForReview")
            .WithSummary("Problems drivers reported with places, by status (default Pending), oldest first.");
        mod.MapPost("/{id:guid}/approve", Approve).WithName("ApprovePlaceProblem")
            .WithSummary("Applies the fix: moves the place (WRONG_POSITION) or hides it (CLOSED, NOT_THERE).");
        mod.MapPost("/{id:guid}/reject", Reject).WithName("RejectPlaceProblem");
        return v1;
    }

    private static bool ValidPosition(double? lat, double? lon) => lat is >= -90 and <= 90 && lon is >= -180 and <= 180;

    private static async Task<Results<Created<PlaceProblemDto>, Ok<PlaceProblemDto>, ValidationProblem, NotFound, Conflict<string>>> Report(
        PlaceProblemRequest r, HttpContext http, RoadPulseDb db, TimeProvider clock)
    {
        var errors = new Dictionary<string, string[]>();
        if (string.IsNullOrWhiteSpace(r.PlaceId) || r.PlaceId.Length > 64) errors["placeId"] = ["The id of a place from search."];
        if (!Enum.IsDefined(r.Kind)) errors["kind"] = [$"One of {string.Join(", ", Enum.GetNames<PlaceProblemKind>())}."];
        var hasPosition = r.Lat is not null || r.Lon is not null;
        if (hasPosition && !ValidPosition(r.Lat, r.Lon)) errors["position"] = ["Coordinates out of range."];
        var note = string.IsNullOrWhiteSpace(r.Note) ? null : r.Note.Trim();
        if (note is { Length: > 200 }) errors["note"] = ["At most 200 characters."];
        // A wrong position needs something to go on: the right position, or words saying where it is.
        if (r.Kind == PlaceProblemKind.WRONG_POSITION && !hasPosition && note is null)
            errors["position"] = ["Give the right position, or a note saying where the place is."];
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);

        var place = await db.DirectoryPlaces.AsNoTracking().FirstOrDefaultAsync(p => p.Id == r.PlaceId);
        if (place is null) return TypedResults.NotFound();
        Point? suggested = null;
        if (r.Kind == PlaceProblemKind.WRONG_POSITION && hasPosition)
        {
            suggested = Geo.CreatePoint(new Coordinate(r.Lon!.Value, r.Lat!.Value));
            if (!await db.DirectoryPlaces.AnyAsync(p => p.Id == r.PlaceId && p.Location.IsWithinDistance(suggested, MaxMoveM)))
                return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["position"] = ["Too far from where the place is now."] });
        }

        var me = http.User.DeviceId();
        // The same driver reporting the same thing again: keep one, with the latest details.
        var existing = await db.PlaceProblems.FirstOrDefaultAsync(p =>
            p.DeviceId == me && p.PlaceId == r.PlaceId && p.Kind == r.Kind && p.Status == CommunityPlaceStatus.Pending);
        if (existing is not null)
        {
            existing.Suggested = suggested ?? existing.Suggested;
            existing.Note = note ?? existing.Note;
            await db.SaveChangesAsync();
            return TypedResults.Ok(ToDto(existing));
        }
        if (await db.PlaceProblems.CountAsync(p => p.DeviceId == me && p.Status == CommunityPlaceStatus.Pending) >= MaxPendingPerDevice)
            return TypedResults.Conflict($"You have {MaxPendingPerDevice} reports waiting for review; please wait until they are checked.");

        var problem = new PlaceProblem
        {
            Id = Guid.NewGuid(), DeviceId = me, PlaceId = place.Id, PlaceName = place.Name, Kind = r.Kind,
            Suggested = suggested, Note = note, CreatedAt = clock.GetUtcNow(),
        };
        db.PlaceProblems.Add(problem);
        await db.SaveChangesAsync();
        return TypedResults.Created($"/v1/places/problems/{problem.Id}", ToDto(problem));
    }

    private static async Task<Results<Ok<List<PlaceProblemReviewItem>>, ValidationProblem>> ReviewList(CommunityPlaceStatus? status, int? limit, RoadPulseDb db)
    {
        var take = limit ?? 100;
        if (take is < 1 or > 500)
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["limit"] = ["Must be between 1 and 500."] });
        var wanted = status ?? CommunityPlaceStatus.Pending;
        var problems = await db.PlaceProblems.AsNoTracking().Where(p => p.Status == wanted)
            .OrderBy(p => wanted == CommunityPlaceStatus.Pending ? p.CreatedAt : DateTimeOffset.MinValue)
            .ThenByDescending(p => p.CreatedAt)
            .Take(take).ToListAsync();
        var ids = problems.Select(p => p.PlaceId).Distinct().ToList();
        var places = await db.DirectoryPlaces.AsNoTracking().Where(p => ids.Contains(p.Id)).ToDictionaryAsync(p => p.Id);
        var devices = problems.Select(p => p.DeviceId).Distinct().ToList();
        var stats = await db.PlaceProblems.AsNoTracking().Where(p => devices.Contains(p.DeviceId))
            .GroupBy(p => p.DeviceId)
            .Select(g => new { g.Key, Total = g.Count(), Rejected = g.Count(p => p.Status == CommunityPlaceStatus.Rejected) })
            .ToDictionaryAsync(x => x.Key);
        return TypedResults.Ok(problems.Select(p =>
        {
            places.TryGetValue(p.PlaceId, out var place);
            double? move = place is not null && p.Suggested is not null
                ? Math.Round(Metres(place.Location.Y, place.Location.X, p.Suggested.Y, p.Suggested.X)) : null;
            return new PlaceProblemReviewItem(ToDto(p), p.CreatedAt, p.DeviceId, stats[p.DeviceId].Total, stats[p.DeviceId].Rejected,
                place?.Address, place?.Location.Y, place?.Location.X, move);
        }).ToList());
    }

    private static async Task<Results<NoContent, NotFound, ValidationProblem>> Approve(
        Guid id, PlaceProblemDecision body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock)
    {
        if (BadReason(body.Reason) is { } bad) return bad;
        var problem = await db.PlaceProblems.FindAsync(id);
        if (problem is null) return TypedResults.NotFound();
        var now = clock.GetUtcNow();
        var correction = await db.PlaceCorrections.FindAsync(problem.PlaceId);
        if (correction is null)
        {
            correction = new PlaceCorrection { PlaceId = problem.PlaceId };
            db.PlaceCorrections.Add(correction);
        }
        correction.UpdatedAt = now;

        if (problem.Kind == PlaceProblemKind.WRONG_POSITION)
        {
            // The moderator's position wins; otherwise the driver's.
            var given = body.Lat is not null || body.Lon is not null;
            if (given && !ValidPosition(body.Lat, body.Lon))
                return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["position"] = ["Coordinates out of range."] });
            var to = given ? Geo.CreatePoint(new Coordinate(body.Lon!.Value, body.Lat!.Value)) : problem.Suggested;
            if (to is null)
                return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["position"] = ["The driver gave no position: type the right latitude and longitude."] });
            correction.Location = to;
            correction.Hidden = false;
            problem.Suggested = to;
            await db.DirectoryPlaces.Where(p => p.Id == problem.PlaceId).ExecuteUpdateAsync(s => s.SetProperty(p => p.Location, to));
        }
        else
        {
            correction.Hidden = true;
            await db.DirectoryPlaces.Where(p => p.Id == problem.PlaceId).ExecuteDeleteAsync();
        }
        Close(problem, CommunityPlaceStatus.Approved, "APPROVE_PLACE_PROBLEM", body.Reason, user, db, now);
        await db.SaveChangesAsync();
        return TypedResults.NoContent();
    }

    private static async Task<Results<NoContent, NotFound, ValidationProblem>> Reject(
        Guid id, ModerationReason body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock)
    {
        if (BadReason(body.Reason) is { } bad) return bad;
        var problem = await db.PlaceProblems.FindAsync(id);
        if (problem is null) return TypedResults.NotFound();
        Close(problem, CommunityPlaceStatus.Rejected, "REJECT_PLACE_PROBLEM", body.Reason, user, db, clock.GetUtcNow());
        await db.SaveChangesAsync();
        return TypedResults.NoContent();
    }

    private static ValidationProblem? BadReason(string? reason) =>
        string.IsNullOrWhiteSpace(reason) || reason.Trim().Length is < 3 or > 500
            ? TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["reason"] = ["3 to 500 characters."] })
            : null;

    private static void Close(PlaceProblem problem, CommunityPlaceStatus status, string action, string reason,
        ClaimsPrincipal user, RoadPulseDb db, DateTimeOffset now)
    {
        problem.Status = status;
        problem.ReviewedAt = now;
        db.AuditLog.Add(new AuditEntry
        {
            At = now, Moderator = user.Identity?.Name ?? "moderator", Action = action, TargetType = "place_problem",
            TargetId = problem.Id, Reason = reason.Trim(),
        });
    }

    private static double Metres(double lat1, double lon1, double lat2, double lon2)
    {
        var k = Math.Cos(lat1 * Math.PI / 180);
        var dx = (lon2 - lon1) * k * 111_320;
        var dy = (lat2 - lat1) * 110_540;
        return Math.Sqrt(dx * dx + dy * dy);
    }

    private static PlaceProblemDto ToDto(PlaceProblem p) =>
        new(p.Id, p.PlaceId, p.PlaceName, p.Kind, p.Note, p.Suggested?.Y, p.Suggested?.X, p.Status);
}
