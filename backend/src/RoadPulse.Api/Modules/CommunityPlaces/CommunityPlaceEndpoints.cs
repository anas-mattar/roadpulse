using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Moderation;

namespace RoadPulse.Api.Modules.CommunityPlaces;

public record AddPlaceRequest(string Name, CommunityPlaceKind Kind, double Lat, double Lon, string? Note);
public record CommunityPlaceDto(Guid Id, string Name, CommunityPlaceKind Kind, string? Note, double Lat, double Lon,
    double DistanceM, CommunityPlaceStatus Status, bool Own);
public record PlaceReviewItem(CommunityPlaceDto Place, DateTimeOffset CreatedAt, Guid DeviceId, int DevicePlaces, int DeviceRejected);

/// <summary>
/// "Add a missing place": drivers add shops, clinics, condo entrances… that search does not know.
/// A new place is searchable at once by the driver who added it, and by everyone once a moderator
/// approves it (admin page, "New places").
/// </summary>
public static class CommunityPlaceEndpoints
{
    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);
    public const int MaxPendingPerDevice = 20;
    public const double SameSpotM = 50;
    public const double SearchRadiusM = 100_000;

    public static RouteGroupBuilder MapCommunityPlaceEndpoints(this RouteGroupBuilder v1)
    {
        var places = v1.MapGroup("/places/community").WithTags("Community places").RequireAuthorization();
        places.MapPost("", Add).RequireRateLimiting(RateLimits.Reports)
            .WithName("AddCommunityPlace").WithSummary("Adds a place search does not know. Visible to others after review.");
        places.MapGet("/search", Search).RequireRateLimiting(RateLimits.Reads)
            .WithName("SearchCommunityPlaces")
            .WithSummary("Approved places (and this device's own) whose name contains q, within 100 km of lat/lon, nearest first.");
        places.MapDelete("/{id:guid}", Remove).RequireRateLimiting(RateLimits.Votes)
            .WithName("RemoveCommunityPlace").WithSummary("Removes a place this device added.");

        var mod = v1.MapGroup("/moderation/places").WithTags("Moderation")
            .RequireAuthorization(ModeratorAuthHandler.Policy).RequireRateLimiting(RateLimits.Moderation);
        mod.MapGet("", ReviewList).WithName("CommunityPlacesForReview")
            .WithSummary("Places drivers added, by status (default Pending), oldest first.");
        mod.MapPost("/{id:guid}/approve", Approve).WithName("ApproveCommunityPlace");
        mod.MapPost("/{id:guid}/reject", Reject).WithName("RejectCommunityPlace");
        return v1;
    }

    /// <summary>Lower-case letters and digits only: "M-City Ampang" → "mcityampang".</summary>
    public static string Key(string text) =>
        new(text.ToLowerInvariant().Where(char.IsLetterOrDigit).ToArray());

    private static async Task<Results<Created<CommunityPlaceDto>, Ok<CommunityPlaceDto>, ValidationProblem, Conflict<string>>> Add(
        AddPlaceRequest r, HttpContext http, RoadPulseDb db, TimeProvider clock)
    {
        var errors = new Dictionary<string, string[]>();
        var name = r.Name?.Trim() ?? "";
        if (name.Length is < 2 or > 80 || Key(name).Length < 2) errors["name"] = ["2 to 80 characters, with letters or digits."];
        if (!Enum.IsDefined(r.Kind)) errors["kind"] = [$"One of {string.Join(", ", Enum.GetNames<CommunityPlaceKind>())}."];
        if (r.Lat is < -90 or > 90 || r.Lon is < -180 or > 180) errors["position"] = ["Coordinates out of range."];
        var note = string.IsNullOrWhiteSpace(r.Note) ? null : r.Note.Trim();
        if (note is { Length: > 120 }) errors["note"] = ["At most 120 characters."];
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);

        var me = http.User.DeviceId();
        var key = Key(name);
        var here = Geo.CreatePoint(new Coordinate(r.Lon, r.Lat));
        // The same name at the same spot: return the place that is already there.
        var existing = await db.CommunityPlaces.AsNoTracking()
            .Where(p => p.SearchKey == key && p.Status != CommunityPlaceStatus.Rejected && p.Location.IsWithinDistance(here, SameSpotM))
            .FirstOrDefaultAsync();
        if (existing is not null) return TypedResults.Ok(ToDto(existing, 0, me));
        if (await db.CommunityPlaces.CountAsync(p => p.DeviceId == me && p.Status == CommunityPlaceStatus.Pending) >= MaxPendingPerDevice)
            return TypedResults.Conflict($"You have {MaxPendingPerDevice} places waiting for review; please wait until they are checked.");

        var place = new CommunityPlace
        {
            Id = Guid.NewGuid(), DeviceId = me, Name = name, Kind = r.Kind, Note = note, SearchKey = key,
            Location = here, CreatedAt = clock.GetUtcNow(),
        };
        db.CommunityPlaces.Add(place);
        await db.SaveChangesAsync();
        return TypedResults.Created($"/v1/places/community/{place.Id}", ToDto(place, 0, me));
    }

    private static async Task<Results<Ok<List<CommunityPlaceDto>>, ValidationProblem>> Search(
        string? q, double? lat, double? lon, int? limit, HttpContext http, RoadPulseDb db)
    {
        var errors = new Dictionary<string, string[]>();
        var key = Key(q ?? "");
        if (key.Length is < 2 or > 80) errors["q"] = ["2 to 80 letters or digits."];
        if (lat is not (>= -90 and <= 90) || lon is not (>= -180 and <= 180)) errors["position"] = ["lat and lon are required."];
        var take = limit ?? 10;
        if (take is < 1 or > 30) errors["limit"] = ["Must be between 1 and 30."];
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);

        var me = http.User.DeviceId();
        var here = Geo.CreatePoint(new Coordinate(lon!.Value, lat!.Value));
        var rows = await db.CommunityPlaces.AsNoTracking()
            .Where(p => (p.Status == CommunityPlaceStatus.Approved || (p.DeviceId == me && p.Status == CommunityPlaceStatus.Pending))
                        && p.SearchKey.Contains(key) && p.Location.IsWithinDistance(here, SearchRadiusM))
            .Select(p => new { Place = p, Distance = p.Location.Distance(here) })
            .OrderBy(x => x.Distance)
            .Take(take)
            .ToListAsync();
        return TypedResults.Ok(rows.Select(x => ToDto(x.Place, x.Distance, me)).ToList());
    }

    private static async Task<Results<NoContent, NotFound>> Remove(Guid id, HttpContext http, RoadPulseDb db)
    {
        var me = http.User.DeviceId();
        var n = await db.CommunityPlaces.Where(p => p.Id == id && p.DeviceId == me).ExecuteDeleteAsync();
        return n == 0 ? TypedResults.NotFound() : TypedResults.NoContent();
    }

    private static async Task<Results<Ok<List<PlaceReviewItem>>, ValidationProblem>> ReviewList(CommunityPlaceStatus? status, int? limit, RoadPulseDb db)
    {
        var take = limit ?? 100;
        if (take is < 1 or > 500)
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["limit"] = ["Must be between 1 and 500."] });
        var wanted = status ?? CommunityPlaceStatus.Pending;
        var places = await db.CommunityPlaces.AsNoTracking().Where(p => p.Status == wanted)
            .OrderBy(p => wanted == CommunityPlaceStatus.Pending ? p.CreatedAt : DateTimeOffset.MinValue)
            .ThenByDescending(p => p.CreatedAt)
            .Take(take).ToListAsync();
        var devices = places.Select(p => p.DeviceId).Distinct().ToList();
        var stats = await db.CommunityPlaces.AsNoTracking().Where(p => devices.Contains(p.DeviceId))
            .GroupBy(p => p.DeviceId)
            .Select(g => new { g.Key, Total = g.Count(), Rejected = g.Count(p => p.Status == CommunityPlaceStatus.Rejected) })
            .ToDictionaryAsync(x => x.Key);
        return TypedResults.Ok(places.Select(p => new PlaceReviewItem(ToDto(p, 0, Guid.Empty), p.CreatedAt, p.DeviceId,
            stats[p.DeviceId].Total, stats[p.DeviceId].Rejected)).ToList());
    }

    private static Task<Results<NoContent, NotFound, ValidationProblem>> Approve(
        Guid id, ModerationReason body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock) =>
        Review(id, body, user, db, clock, CommunityPlaceStatus.Approved, "APPROVE_PLACE");

    private static Task<Results<NoContent, NotFound, ValidationProblem>> Reject(
        Guid id, ModerationReason body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock) =>
        Review(id, body, user, db, clock, CommunityPlaceStatus.Rejected, "REJECT_PLACE");

    private static async Task<Results<NoContent, NotFound, ValidationProblem>> Review(
        Guid id, ModerationReason body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock, CommunityPlaceStatus status, string action)
    {
        if (string.IsNullOrWhiteSpace(body.Reason) || body.Reason.Trim().Length is < 3 or > 500)
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["reason"] = ["3 to 500 characters."] });
        var place = await db.CommunityPlaces.FindAsync(id);
        if (place is null) return TypedResults.NotFound();
        var now = clock.GetUtcNow();
        place.Status = status;
        place.ReviewedAt = now;
        db.AuditLog.Add(new AuditEntry
        {
            At = now, Moderator = user.Identity?.Name ?? "moderator", Action = action, TargetType = "place",
            TargetId = id, Reason = body.Reason.Trim(),
        });
        await db.SaveChangesAsync();
        return TypedResults.NoContent();
    }

    private static CommunityPlaceDto ToDto(CommunityPlace p, double distance, Guid me) =>
        new(p.Id, p.Name, p.Kind, p.Note, p.Location.Y, p.Location.X, Math.Round(distance), p.Status, p.DeviceId == me);
}
