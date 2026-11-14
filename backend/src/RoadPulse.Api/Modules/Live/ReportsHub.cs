using Microsoft.AspNetCore.SignalR;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Reports;

namespace RoadPulse.Api.Modules.Live;

/// <summary>
/// Live report updates over SignalR at /hubs/reports. The map is split into 0.1° tiles (about 11 km);
/// a client subscribes to the area it shows and joins the groups for the tiles it covers. Events:
/// <c>ReportUpserted(ReportDto)</c> for new, confirmed or restored reports and
/// <c>ReportRemoved(Guid id)</c> for reports taken off the map. Natural expiry is not pushed: clients
/// drop reports locally at <c>expiresAt</c>. Public, like GET /v1/reports, and sends the same data.
/// </summary>
public class ReportsHub : Hub
{
    public const string Path = "/hubs/reports";
    public const string Upserted = "ReportUpserted";
    public const string Removed = "ReportRemoved";
    public const double TileDegrees = 0.1;
    private const string TilesKey = "tiles";

    /// <summary>Replaces the connection's area (call again when the map moves). Returns the tile count.</summary>
    public async Task<int> SubscribeArea(double minLon, double minLat, double maxLon, double maxLat)
    {
        if (!ReportEndpoints.TryCreateBox(minLon, minLat, maxLon, maxLat, out _, out var error))
            throw new HubException(error);

        var wanted = TilesFor(minLon, minLat, maxLon, maxLat).ToHashSet();
        var current = Context.Items.TryGetValue(TilesKey, out var o) ? (HashSet<string>)o! : new HashSet<string>();
        foreach (var g in current.Except(wanted)) await Groups.RemoveFromGroupAsync(Context.ConnectionId, g);
        foreach (var g in wanted.Except(current)) await Groups.AddToGroupAsync(Context.ConnectionId, g);
        Context.Items[TilesKey] = wanted;
        return wanted.Count;
    }

    public static string TileFor(double lon, double lat) =>
        $"tile:{(int)Math.Floor(lon / TileDegrees)}:{(int)Math.Floor(lat / TileDegrees)}";

    public static IEnumerable<string> TilesFor(double minLon, double minLat, double maxLon, double maxLat)
    {
        for (var x = (int)Math.Floor(minLon / TileDegrees); x <= (int)Math.Floor(maxLon / TileDegrees); x++)
        for (var y = (int)Math.Floor(minLat / TileDegrees); y <= (int)Math.Floor(maxLat / TileDegrees); y++)
            yield return $"tile:{x}:{y}";
    }
}

/// <summary>Sends report changes to the clients watching the report's tile.</summary>
public class LiveReports(IHubContext<ReportsHub> hub, TimeProvider clock, ILogger<LiveReports> log)
{
    /// <summary>Pushes the report's current state: an update if it is on the map, otherwise a removal.</summary>
    public async Task PublishAsync(Report report)
    {
        try
        {
            var group = hub.Clients.Group(ReportsHub.TileFor(report.Location.X, report.Location.Y));
            if (report.Status == ReportStatus.Active && report.ExpiresAt > clock.GetUtcNow())
                await group.SendAsync(ReportsHub.Upserted, ReportEndpoints.ToDto(report));
            else
                await group.SendAsync(ReportsHub.Removed, report.Id);
        }
        catch (Exception e)
        {
            // The change is already saved; clients still see it on their next list request.
            log.LogWarning(e, "Live update failed for report {ReportId}", report.Id);
        }
    }

    public async Task PublishAsync(IEnumerable<Report> reports)
    {
        foreach (var r in reports) await PublishAsync(r);
    }
}
