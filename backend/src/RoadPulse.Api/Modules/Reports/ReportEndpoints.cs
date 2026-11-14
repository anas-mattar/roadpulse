using System.ComponentModel.DataAnnotations;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Live;

namespace RoadPulse.Api.Modules.Reports;

public record CreateReportRequest(ReportType Type, double Lat, double Lon, short? HeadingDeg,
    string? Subtype = null, int? DurationMinutes = null);
public record VoteRequest(bool StillThere);
public record FlagRequest(FlagReason Reason);
public record ReportDto(Guid Id, ReportType Type, double Lat, double Lon, short? HeadingDeg,
    DateTimeOffset CreatedAt, DateTimeOffset ExpiresAt, int Confirmations, int Dismissals, string? Subtype = null);
public record CreateReportResponse(ReportDto Report, bool Merged);

public static class ReportEndpoints
{
    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);

    /// <summary>Largest map area one request may ask for (about 55 km square), to bound query cost.</summary>
    public const double MaxBboxDegrees = 0.5;
    public const int MaxResults = 500;

    public static RouteGroupBuilder MapReportEndpoints(this RouteGroupBuilder v1)
    {
        var reports = v1.MapGroup("/reports").WithTags("Reports");

        reports.MapGet("/", ListReports)
            .RequireRateLimiting(RateLimits.Reads)
            .WithName("ListReports")
            .WithSummary("Active reports inside a bounding box: bbox=minLon,minLat,maxLon,maxLat.");

        reports.MapPost("/", CreateReport)
            .RequireAuthorization()
            .RequireRateLimiting(RateLimits.Reports)
            .WithName("CreateReport")
            .WithSummary("Creates a report, or confirms an identical nearby one. Supports Idempotency-Key.");

        reports.MapPost("/{id:guid}/votes", Vote)
            .RequireAuthorization()
            .RequireRateLimiting(RateLimits.Votes)
            .WithName("VoteOnReport")
            .WithSummary("Still there / not there. One vote per device; voting again replaces it.");

        reports.MapPost("/{id:guid}/flags", Flag)
            .RequireAuthorization()
            .RequireRateLimiting(RateLimits.Votes)
            .WithName("FlagReport")
            .WithSummary("Flags a report as abuse (fake, spam, offensive, wrong location). One flag per device.");

        return v1;
    }

    private static async Task<Results<NoContent, NotFound, Conflict<string>, ValidationProblem>> Flag(
        Guid id, FlagRequest request, HttpContext http, RoadPulseDb db, TimeProvider clock, IConfiguration config, LiveReports live)
    {
        if (!Enum.IsDefined(request.Reason))
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["reason"] = ["Unknown reason."] });
        var deviceId = http.User.DeviceId();
        var now = clock.GetUtcNow();
        var report = await db.Reports.Include(r => r.FlagEntries)
            .FirstOrDefaultAsync(r => r.Id == id && r.Status != ReportStatus.Removed && r.ExpiresAt > now);
        if (report is null) return TypedResults.NotFound();
        if (report.DeviceId == deviceId) return TypedResults.Conflict("You cannot flag your own report.");
        if (report.FlagEntries.Any(f => f.DeviceId == deviceId)) return TypedResults.NoContent();

        report.FlagEntries.Add(new ReportFlag { ReportId = id, DeviceId = deviceId, Reason = request.Reason, CreatedAt = now });
        report.Flags++;
        if (report.Status == ReportStatus.Active
            && ReportRules.ShouldHideForReview(report.Flags, report.Reviewed, config.GetValue("Moderation:AutoHideFlags", ReportRules.DefaultAutoHideFlags)))
            report.Status = ReportStatus.PendingReview;
        await db.SaveChangesAsync();
        if (report.Status == ReportStatus.PendingReview) await live.PublishAsync(report);
        return TypedResults.NoContent();
    }

    private static async Task<Results<Ok<List<ReportDto>>, ValidationProblem>> ListReports(
        string? bbox, RoadPulseDb db, TimeProvider clock)
    {
        if (!TryParseBbox(bbox, out var box, out var error))
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["bbox"] = [error] });

        var now = clock.GetUtcNow();
        var area = Geo.ToGeometry(box);
        var list = await db.Reports.AsNoTracking()
            .Where(r => r.Status == ReportStatus.Active && r.ExpiresAt > now && r.Location.Intersects(area))
            .OrderByDescending(r => r.CreatedAt)
            .Take(MaxResults)
            .ToListAsync();
        return TypedResults.Ok(list.Select(ToDto).ToList());
    }

    private static async Task<Results<Created<CreateReportResponse>, Ok<CreateReportResponse>, ValidationProblem>> CreateReport(
        CreateReportRequest request, HttpContext http, RoadPulseDb db, TimeProvider clock, LiveReports live)
    {
        var errors = Validate(request);
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);

        var deviceId = http.User.DeviceId();
        var now = clock.GetUtcNow();
        var key = http.Request.Headers["Idempotency-Key"].ToString();
        if (key.Length > 100) return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["Idempotency-Key"] = ["At most 100 characters."] });

        // Retry of an earlier request: return the same report, create nothing.
        if (key.Length > 0)
        {
            var previous = await db.IdempotencyRecords.AsNoTracking()
                .FirstOrDefaultAsync(i => i.DeviceId == deviceId && i.Key == key && i.CreatedAt > now.AddHours(-24));
            if (previous is not null && await db.Reports.FindAsync(previous.ReportId) is { } earlier)
                return TypedResults.Ok(new CreateReportResponse(ToDto(earlier), Merged: false));
        }

        var point = Geo.CreatePoint(new Coordinate(request.Lon, request.Lat));
        var windowStart = now - ReportRules.DuplicateWindow;

        // Same type close by and recent: treat as a confirmation of the existing report.
        var existing = await db.Reports.Include(r => r.Votes)
            .Where(r => r.Type == request.Type && r.Status == ReportStatus.Active && r.ExpiresAt > now && r.CreatedAt > windowStart
                        && r.Location.IsWithinDistance(point, ReportRules.DuplicateRadiusMeters))
            .OrderBy(r => r.CreatedAt)
            .FirstOrDefaultAsync();

        Report report;
        bool merged;
        if (existing is not null)
        {
            merged = true;
            report = existing;
            if (existing.DeviceId != deviceId && existing.Votes.All(v => v.DeviceId != deviceId))
            {
                existing.Votes.Add(new ReportVote { ReportId = existing.Id, DeviceId = deviceId, StillThere = true, CreatedAt = now });
                existing.Confirmations++;
                existing.ExpiresAt = ReportRules.ExtendOnConfirm(existing.Type, existing.CreatedAt, existing.ExpiresAt, now);
            }
        }
        else
        {
            merged = false;
            report = new Report
            {
                Id = Guid.NewGuid(), Type = request.Type, Location = point, HeadingDeg = request.HeadingDeg,
                DeviceId = deviceId, CreatedAt = now, Subtype = request.Subtype,
                ExpiresAt = ReportRules.InitialExpiry(request.Type, now, request.DurationMinutes),
            };
            db.Reports.Add(report);
        }

        if (key.Length > 0)
            db.IdempotencyRecords.Add(new IdempotencyRecord { DeviceId = deviceId, Key = key, ReportId = report.Id, CreatedAt = now });

        await db.SaveChangesAsync();
        await live.PublishAsync(report);
        var body = new CreateReportResponse(ToDto(report), merged);
        return merged ? TypedResults.Ok(body) : TypedResults.Created($"/v1/reports/{report.Id}", body);
    }

    private static async Task<Results<Ok<ReportDto>, NotFound, Conflict<string>>> Vote(
        Guid id, VoteRequest request, HttpContext http, RoadPulseDb db, TimeProvider clock, LiveReports live)
    {
        var deviceId = http.User.DeviceId();
        var now = clock.GetUtcNow();
        var report = await db.Reports.Include(r => r.Votes).FirstOrDefaultAsync(r => r.Id == id && r.Status == ReportStatus.Active && r.ExpiresAt > now);
        if (report is null) return TypedResults.NotFound();
        if (report.DeviceId == deviceId) return TypedResults.Conflict("You cannot vote on your own report.");

        var vote = report.Votes.FirstOrDefault(v => v.DeviceId == deviceId);
        if (vote is null)
        {
            report.Votes.Add(new ReportVote { ReportId = id, DeviceId = deviceId, StillThere = request.StillThere, CreatedAt = now });
            if (request.StillThere) report.Confirmations++; else report.Dismissals++;
        }
        else if (vote.StillThere != request.StillThere)
        {
            vote.StillThere = request.StillThere;
            vote.CreatedAt = now;
            if (request.StillThere) { report.Confirmations++; report.Dismissals--; }
            else { report.Dismissals++; report.Confirmations--; }
        }

        if (request.StillThere)
            report.ExpiresAt = ReportRules.ExtendOnConfirm(report.Type, report.CreatedAt, report.ExpiresAt, now);
        else if (ReportRules.ShouldRemove(report.Confirmations, report.Dismissals))
            report.ExpiresAt = now;

        await db.SaveChangesAsync();
        await live.PublishAsync(report);
        return TypedResults.Ok(ToDto(report));
    }

    private static Dictionary<string, string[]> Validate(CreateReportRequest r)
    {
        var errors = new Dictionary<string, string[]>();
        if (!Enum.IsDefined(r.Type)) errors["type"] = ["Unknown report type."];
        if (r.Lat is < -90 or > 90 || double.IsNaN(r.Lat)) errors["lat"] = ["Must be between -90 and 90."];
        if (r.Lon is < -180 or > 180 || double.IsNaN(r.Lon)) errors["lon"] = ["Must be between -180 and 180."];
        if (r.HeadingDeg is < 0 or >= 360) errors["headingDeg"] = ["Must be between 0 and 359."];
        if (Enum.IsDefined(r.Type) && !ReportRules.IsValidSubtype(r.Type, r.Subtype))
            errors["subtype"] = [$"For {r.Type}: one of {string.Join(", ", ReportRules.Subtypes[r.Type])}."];
        if (r.DurationMinutes is not null && Enum.IsDefined(r.Type) && !ReportRules.AllowsDuration(r.Type))
            errors["durationMinutes"] = ["Only road closures take a duration."];
        else if (r.DurationMinutes is < ReportRules.MinDurationMinutes or > 24 * 60)
            errors["durationMinutes"] = [$"Must be between {ReportRules.MinDurationMinutes} and 1440 minutes."];
        return errors;
    }

    private static bool TryParseBbox(string? bbox, out Envelope box, out string error)
    {
        box = new Envelope();
        error = "Expected minLon,minLat,maxLon,maxLat.";
        var parts = bbox?.Split(',');
        if (parts is not { Length: 4 }) return false;
        var v = new double[4];
        for (var i = 0; i < 4; i++)
            if (!double.TryParse(parts[i], System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out v[i])) return false;
        return TryCreateBox(v[0], v[1], v[2], v[3], out box, out error);
    }

    /// <summary>Validates a map area (also used by the live updates hub).</summary>
    public static bool TryCreateBox(double minLon, double minLat, double maxLon, double maxLat, out Envelope box, out string error)
    {
        box = new Envelope();
        error = "Expected minLon < maxLon and minLat < maxLat, within world bounds.";
        if (double.IsNaN(minLon + minLat + maxLon + maxLat)) return false;
        if (minLon >= maxLon || minLat >= maxLat || minLon < -180 || maxLon > 180 || minLat < -90 || maxLat > 90) return false;
        if (maxLon - minLon > MaxBboxDegrees || maxLat - minLat > MaxBboxDegrees)
        {
            error = $"Area too large: at most {MaxBboxDegrees} degrees per side.";
            return false;
        }
        box = new Envelope(minLon, maxLon, minLat, maxLat);
        return true;
    }

    internal static ReportDto ToDto(Report r) => new(r.Id, r.Type, r.Location.Y, r.Location.X, r.HeadingDeg,
        r.CreatedAt, r.ExpiresAt, r.Confirmations, r.Dismissals, r.Subtype);
}
