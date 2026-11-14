using System.Globalization;
using System.Text;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using RoadPulse.Api.Data;

namespace RoadPulse.Api.Modules.Traffic;

public record TrafficSegmentRequest(List<TrafficPoint> Points);
public enum TrafficLevel { FREE, SLOW, JAM }
public record TrafficLineDto(long EdgeId, TrafficLevel Level, int SpeedKph, int ExpectedKph, List<double[]> Points);

/// <summary>Privacy and display rules for live traffic (ADR 0004). Pure: unit tested.</summary>
public static class TrafficRules
{
    /// <summary>A road speed is shown only when at least this many separate segments contributed.</summary>
    public const int MinSegments = 3;
    public static readonly TimeSpan LiveWindow = TimeSpan.FromMinutes(15);
    public static readonly TimeSpan Bucket = TimeSpan.FromMinutes(5);
    public static readonly TimeSpan Retention = TimeSpan.FromDays(365);
    public const int MinPoints = 5;
    public const int MaxPoints = 200;
    public const int MaxSegmentSeconds = 180;

    public static DateTimeOffset WindowStart(DateTimeOffset t) =>
        new(t.UtcTicks - t.UtcTicks % Bucket.Ticks, TimeSpan.Zero);

    /// <summary>Green when traffic moves at 70% or more of the normal speed, red below 40%.</summary>
    public static TrafficLevel Level(double speedKph, int expectedKph)
    {
        var ratio = speedKph / Math.Max(expectedKph, 10);
        return ratio >= 0.7 ? TrafficLevel.FREE : ratio >= 0.4 ? TrafficLevel.SLOW : TrafficLevel.JAM;
    }

    public static string? Validate(TrafficSegmentRequest r)
    {
        if (r.Points is not { Count: >= MinPoints and <= MaxPoints }) return $"Between {MinPoints} and {MaxPoints} points.";
        if (r.Points.Any(p => p.Lat is < -90 or > 90 || p.Lon is < -180 or > 180)) return "Coordinates out of range.";
        for (var i = 1; i < r.Points.Count; i++)
            if (r.Points[i].T < r.Points[i - 1].T) return "Times must not go backwards.";
        if (r.Points[0].T < 0 || r.Points[^1].T - r.Points[0].T > MaxSegmentSeconds) return $"A segment covers at most {MaxSegmentSeconds} seconds.";
        return null;
    }
}

/// <summary>
/// Live traffic from opted-in drivers (ADR 0004). Uploads are anonymous (no device token, no id
/// kept): each 2-minute segment is map-matched at once, added to per-road 5-minute averages, and
/// the points are dropped. Only roads with at least <see cref="TrafficRules.MinSegments"/> segments
/// in the last 15 minutes are shown.
/// </summary>
public static class TrafficEndpoints
{
    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);

    public static RouteGroupBuilder MapTrafficEndpoints(this RouteGroupBuilder v1)
    {
        var traffic = v1.MapGroup("/traffic").WithTags("Traffic");
        traffic.MapPost("/samples", Ingest).RequireRateLimiting(RateLimits.Traffic)
            .WithName("TrafficSamples").WithSummary("Anonymous speed samples from one segment of a drive (opt-in in the app).");
        traffic.MapGet("", Lines).RequireRateLimiting(RateLimits.Reads)
            .WithName("TrafficLines").WithSummary("Road speeds in a box (minLat, minLon, maxLat, maxLon), last 15 minutes, privacy threshold applied.");
        return v1;
    }

    private static async Task<Results<Accepted, ValidationProblem>> Ingest(
        TrafficSegmentRequest request, IMapMatcher matcher, RoadPulseDb db, TimeProvider clock, CancellationToken ct)
    {
        if (TrafficRules.Validate(request) is { } error)
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["points"] = [error] });
        var edges = await matcher.MatchAsync(request.Points, ct);
        var window = TrafficRules.WindowStart(clock.GetUtcNow());
        foreach (var e in edges)
        {
            var wkt = Wkt(e.Line);
            await db.Database.ExecuteSqlInterpolatedAsync($"""
                INSERT INTO traffic_edges (id, way_id, expected_kph, geometry)
                VALUES ({e.EdgeId}, {e.WayId}, {e.ExpectedKph}, ST_GeogFromText({wkt}))
                ON CONFLICT (id) DO NOTHING
                """, ct);
            await db.Database.ExecuteSqlInterpolatedAsync($"""
                INSERT INTO traffic_observations (edge_id, window_start, speed_sum_kph, segments)
                VALUES ({e.EdgeId}, {window}, {e.SpeedKph}, 1)
                ON CONFLICT (edge_id, window_start) DO UPDATE
                SET speed_sum_kph = traffic_observations.speed_sum_kph + EXCLUDED.speed_sum_kph,
                    segments = traffic_observations.segments + 1
                """, ct);
        }
        return TypedResults.Accepted((string?)null);
    }

    private static async Task<Results<Ok<List<TrafficLineDto>>, ValidationProblem>> Lines(
        double? minLat, double? minLon, double? maxLat, double? maxLon, RoadPulseDb db, TimeProvider clock)
    {
        if (minLat is null || minLon is null || maxLat is null || maxLon is null || minLat >= maxLat || minLon >= maxLon ||
            maxLat - minLat > 1.0 || maxLon - minLon > 1.0)
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["bbox"] = ["minLat < maxLat, minLon < maxLon, at most 1 degree each way."] });
        var box = Geo.CreatePolygon([
            new(minLon.Value, minLat.Value), new(maxLon.Value, minLat.Value), new(maxLon.Value, maxLat.Value),
            new(minLon.Value, maxLat.Value), new(minLon.Value, minLat.Value),
        ]);
        var since = clock.GetUtcNow() - TrafficRules.LiveWindow;
        var speeds = db.TrafficObservations.Where(o => o.WindowStart >= since)
            .GroupBy(o => o.EdgeId)
            .Select(g => new { EdgeId = g.Key, Sum = g.Sum(o => o.SpeedSumKph), Segments = g.Sum(o => o.Segments) })
            .Where(x => x.Segments >= TrafficRules.MinSegments);
        var rows = await speeds
            .Join(db.TrafficEdges.Where(e => e.Geometry.Intersects(box)), s => s.EdgeId, e => e.Id,
                (s, e) => new { e.Id, e.ExpectedKph, e.Geometry, s.Sum, s.Segments })
            .Take(3000)
            .ToListAsync();
        return TypedResults.Ok(rows.Select(r =>
        {
            var kph = r.Sum / r.Segments;
            return new TrafficLineDto(r.Id, TrafficRules.Level(kph, r.ExpectedKph), (int)Math.Round(kph), r.ExpectedKph,
                r.Geometry.Coordinates.Select(c => new[] { c.Y, c.X }).ToList());
        }).ToList());
    }

    private static string Wkt(IReadOnlyList<(double Lat, double Lon)> line)
    {
        var sb = new StringBuilder("SRID=4326;LINESTRING(");
        for (var i = 0; i < line.Count; i++)
        {
            if (i > 0) sb.Append(',');
            sb.Append(line[i].Lon.ToString("R", CultureInfo.InvariantCulture)).Append(' ')
              .Append(line[i].Lat.ToString("R", CultureInfo.InvariantCulture));
        }
        return sb.Append(')').ToString();
    }
}

/// <summary>Deletes aggregated speeds older than the retention period (12 months), once an hour.</summary>
public sealed class TrafficCleanup(IServiceScopeFactory scopes, TimeProvider clock, ILogger<TrafficCleanup> log) : BackgroundService
{
    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        while (!stop.IsCancellationRequested)
        {
            try
            {
                using var scope = scopes.CreateScope();
                var db = scope.ServiceProvider.GetRequiredService<RoadPulseDb>();
                var cutoff = clock.GetUtcNow() - TrafficRules.Retention;
                var n = await db.TrafficObservations.Where(o => o.WindowStart < cutoff).ExecuteDeleteAsync(stop);
                if (n > 0) log.LogInformation("Deleted {Count} old traffic averages", n);
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                log.LogWarning("Traffic cleanup failed: {Error}", e.GetType().Name);
            }
            await Task.Delay(TimeSpan.FromHours(1), stop).ContinueWith(_ => { });
        }
    }
}
