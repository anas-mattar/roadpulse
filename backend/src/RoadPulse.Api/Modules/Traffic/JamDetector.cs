using Microsoft.EntityFrameworkCore;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Live;
using RoadPulse.Api.Modules.Reports;

namespace RoadPulse.Api.Modules.Traffic;

/// <summary>A jammed road edge from live traffic (privacy threshold already applied).</summary>
public record JamEdge(long EdgeId, double MidLat, double MidLon, double LengthM, double SpeedKph);

/// <summary>A stretch of jammed edges close together, reported as one traffic jam.</summary>
public record JamStretch(double Lat, double Lon, double LengthM, double SpeedKph);

/// <summary>
/// Turns live traffic into "Traffic jam" reports, so drivers without the traffic layer are warned
/// too: every 2 minutes, stretches of jammed roads (at least <see cref="MinStretchM"/> long) get a
/// report from the RoadPulse system account, kept alive while the jam lasts and left to expire after.
/// </summary>
public sealed class JamDetector(IServiceScopeFactory scopes, TimeProvider clock, ILogger<JamDetector> log) : BackgroundService
{
    /// <summary>The system "device" that owns automatic reports (no token: it cannot be used to sign in).</summary>
    public static readonly Guid SystemDeviceId = new("00000000-0000-0000-0000-00000000a0a0");
    public const double GroupM = 300;
    public const double MinStretchM = 150;
    /// <summary>An automatic jam report lasts this long unless the jam is seen again.</summary>
    public static readonly TimeSpan ReportLife = TimeSpan.FromMinutes(20);

    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);

    /// <summary>Groups jammed edges whose midpoints are within <see cref="GroupM"/> of the group. Pure: unit tested.</summary>
    public static List<JamStretch> Stretches(IReadOnlyList<JamEdge> edges)
    {
        var groups = new List<List<JamEdge>>();
        foreach (var e in edges.OrderBy(e => e.MidLat).ThenBy(e => e.MidLon))
        {
            var g = groups.FirstOrDefault(g => g.Any(x => Metres(x.MidLat, x.MidLon, e.MidLat, e.MidLon) <= GroupM));
            if (g is null) groups.Add([e]); else g.Add(e);
        }
        return groups
            .Where(g => g.Sum(e => e.LengthM) >= MinStretchM)
            .Select(g =>
            {
                var middle = g.OrderBy(e => e.MidLat).ThenBy(e => e.MidLon).ElementAt(g.Count / 2);
                return new JamStretch(middle.MidLat, middle.MidLon, g.Sum(e => e.LengthM), g.Average(e => e.SpeedKph));
            })
            .ToList();
    }

    private static double Metres(double lat1, double lon1, double lat2, double lon2)
    {
        var k = Math.Cos(lat1 * Math.PI / 180);
        return Math.Sqrt(Math.Pow((lon2 - lon1) * k * 111_320, 2) + Math.Pow((lat2 - lat1) * 110_540, 2));
    }

    /// <summary>One detection pass; returns the reports created or kept alive.</summary>
    public static async Task<List<Report>> RunOnceAsync(RoadPulseDb db, DateTimeOffset now, CancellationToken ct = default)
    {
        var since = now - TrafficRules.LiveWindow;
        var live = await db.TrafficObservations.Where(o => o.WindowStart >= since)
            .GroupBy(o => o.EdgeId)
            .Select(g => new { EdgeId = g.Key, Sum = g.Sum(o => o.SpeedSumKph), Segments = g.Sum(o => o.Segments) })
            .Where(x => x.Segments >= TrafficRules.MinSegments)
            .Join(db.TrafficEdges, s => s.EdgeId, e => e.Id, (s, e) => new { e.Id, e.ExpectedKph, e.Geometry, s.Sum, s.Segments })
            .ToListAsync(ct);
        var jams = live
            .Select(x => new { x.Id, x.Geometry, x.ExpectedKph, Kph = x.Sum / x.Segments })
            .Where(x => TrafficRules.Level(x.Kph, x.ExpectedKph) == TrafficLevel.JAM)
            .Select(x =>
            {
                var c = x.Geometry.Coordinates;
                var mid = c[c.Length / 2];
                double length = 0;
                for (var i = 1; i < c.Length; i++) length += Metres(c[i - 1].Y, c[i - 1].X, c[i].Y, c[i].X);
                return new JamEdge(x.Id, mid.Y, mid.X, length, x.Kph);
            })
            .ToList();
        var stretches = Stretches(jams);
        if (stretches.Count == 0) return [];

        if (!await db.Devices.AnyAsync(d => d.Id == SystemDeviceId, ct))
        {
            db.Devices.Add(new Device { Id = SystemDeviceId, TokenHash = "system-no-token", CreatedAt = now, Nickname = "RoadPulse" });
            await db.SaveChangesAsync(ct);
        }

        var touched = new List<Report>();
        foreach (var s in stretches)
        {
            var point = Geo.CreatePoint(new Coordinate(s.Lon, s.Lat));
            var existing = await db.Reports
                .Where(r => r.Type == ReportType.TRAFFIC_JAM && r.Status == ReportStatus.Active && r.ExpiresAt > now &&
                            r.Location.IsWithinDistance(point, GroupM))
                .FirstOrDefaultAsync(ct);
            var subtype = s.SpeedKph < 8 ? "STANDSTILL" : "HEAVY";
            if (existing is not null)
            {
                // A driver already reported it, or it is ours: keep ours alive while the jam lasts.
                if (existing.DeviceId == SystemDeviceId && existing.ExpiresAt < now + ReportLife)
                {
                    existing.ExpiresAt = now + ReportLife;
                    existing.Subtype = subtype;
                    touched.Add(existing);
                }
                continue;
            }
            var report = new Report
            {
                Id = Guid.NewGuid(), Type = ReportType.TRAFFIC_JAM, Subtype = subtype, Location = point,
                DeviceId = SystemDeviceId, CreatedAt = now, ExpiresAt = now + ReportLife,
            };
            db.Reports.Add(report);
            touched.Add(report);
        }
        await db.SaveChangesAsync(ct);
        return touched;
    }

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        while (!stop.IsCancellationRequested)
        {
            await Task.Delay(TimeSpan.FromMinutes(2), stop).ContinueWith(_ => { });
            if (stop.IsCancellationRequested) break;
            try
            {
                using var scope = scopes.CreateScope();
                var db = scope.ServiceProvider.GetRequiredService<RoadPulseDb>();
                var reports = await RunOnceAsync(db, clock.GetUtcNow(), stop);
                if (reports.Count > 0)
                {
                    await scope.ServiceProvider.GetRequiredService<LiveReports>().PublishAsync(reports);
                    log.LogInformation("Traffic jams: {Count} automatic reports created or extended", reports.Count);
                }
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                log.LogWarning("Jam detection failed: {Error}", e.GetType().Name);
            }
        }
    }
}
