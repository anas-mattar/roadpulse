using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Privacy;
using RoadPulse.Api.Modules.Reports;
using RoadPulse.Api.Modules.Shares;
using RoadPulse.Api.Modules.Traffic;

namespace RoadPulse.Api.Tests;

public class JamStretchTests
{
    [Fact]
    public void NearbyJammedEdgesBecomeOneStretch()
    {
        var edges = new List<JamEdge>
        {
            new(1, 3.1000, 101.600, 120, 6), new(2, 3.1000, 101.601, 120, 8), new(3, 3.1000, 101.602, 120, 10), // ~330 m together
            new(4, 3.2000, 101.700, 60, 5),   // alone and short: not reported
        };
        var s = Assert.Single(JamDetector.Stretches(edges));
        Assert.Equal(360, s.LengthM);
        Assert.Equal(8, s.SpeedKph, 3);
        Assert.Equal(101.601, s.Lon, 6);
    }
}

[Collection("database")]
public class PrivacyApiTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };

    private static async Task<HttpClient> DeviceClient(ApiFactory api)
    {
        var client = await api.ClientAsync();
        var reg = await (await client.PostAsync("/v1/devices", null)).Content.ReadFromJsonAsync<DeviceRegistration>(Json);
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", reg!.Token);
        return client;
    }

    private static async Task<ReportDto> Report(HttpClient c, string type, double lat, double lon) =>
        (await (await c.PostAsJsonAsync("/v1/reports", new { type, lat, lon })).Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report;

    [Fact]
    public async Task ExportThenDeleteEverything()
    {
        using var api = new ApiFactory();
        var me = await DeviceClient(api);
        var other = await DeviceClient(api);
        var mine = await Report(me, "HAZARD", 4.71, 102.71);
        var theirs = await Report(other, "POLICE", 4.73, 102.73);
        (await me.PostAsJsonAsync($"/v1/reports/{theirs.Id}/votes", new { stillThere = true })).EnsureSuccessStatusCode();
        await me.PutAsJsonAsync("/v1/me", new { nickname = "Ahmad" });
        var share = (await (await me.PostAsJsonAsync("/v1/shares", new { destination = "KLCC", destLat = 3.158, destLon = 101.712, lat = 3.1, lon = 101.6, etaUnixMs = 0L }))
            .Content.ReadFromJsonAsync<CreateShareResponse>(Json))!;

        var export = (await me.GetFromJsonAsync<DataExport>("/v1/me/export", Json))!;
        Assert.Equal("Ahmad", export.Nickname);
        Assert.Equal(mine.Id, Assert.Single(export.Reports).Id);
        Assert.Equal(theirs.Id, Assert.Single(export.Votes).ReportId);

        Assert.Equal(HttpStatusCode.NoContent, (await me.DeleteAsync("/v1/me")).StatusCode);

        using var scope = api.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<RoadPulseDb>();
        Assert.False(await db.Reports.AnyAsync(r => r.Id == mine.Id));
        Assert.Equal(0, (await db.Reports.SingleAsync(r => r.Id == theirs.Id)).Confirmations); // my vote no longer counts
        Assert.False(await db.ReportVotes.AnyAsync(v => v.ReportId == theirs.Id));
        Assert.Equal(HttpStatusCode.NotFound, (await other.GetAsync($"/v1/shares/{share.Id}")).StatusCode);
        // The old token no longer works; the app registers again as a new device.
        Assert.Equal(HttpStatusCode.Unauthorized, (await me.GetAsync("/v1/me")).StatusCode);
    }

    [Fact]
    public async Task OldDataIsDeletedAfterTheRetentionPeriod()
    {
        using var api = new ApiFactory();
        var me = await DeviceClient(api);
        var r = await Report(me, "HAZARD", 4.75, 102.75);
        using var scope = api.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<RoadPulseDb>();
        var now = api.Clock.GetUtcNow();
        await PrivacyRetention.RunOnceAsync(db, now.AddDays(30));
        Assert.True(await db.Reports.AnyAsync(x => x.Id == r.Id));
        await PrivacyRetention.RunOnceAsync(db, now.AddDays(100));
        Assert.False(await db.Reports.AnyAsync(x => x.Id == r.Id));
    }

    [Fact]
    public async Task PrivacyPageIsServed()
    {
        using var api = new ApiFactory();
        var c = await api.ClientAsync();
        var page = await c.GetAsync("/privacy");
        Assert.Equal(HttpStatusCode.OK, page.StatusCode);
        Assert.Contains("Delete my data", await page.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task JamDetectionCreatesAndRefreshesOneReport()
    {
        using var api = new ApiFactory();
        await api.ClientAsync();
        using var scope = api.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<RoadPulseDb>();
        var now = api.Clock.GetUtcNow();
        var window = TrafficRules.WindowStart(now);
        // Two jammed edges (5 km/h where 60 is normal), 3 segments each, near 4.9, 102.9.
        for (var i = 0; i < 2; i++)
        {
            var lon = 102.9 + i * 0.002;
            var wkt = FormattableString.Invariant($"SRID=4326;LINESTRING({lon} 4.9,{lon + 0.002} 4.9)");
            await db.Database.ExecuteSqlInterpolatedAsync($"INSERT INTO traffic_edges (id, way_id, expected_kph, geometry) VALUES ({7000 + i}, 1, 60, ST_GeogFromText({wkt})) ON CONFLICT DO NOTHING");
            await db.Database.ExecuteSqlInterpolatedAsync($"INSERT INTO traffic_observations (edge_id, window_start, speed_sum_kph, segments) VALUES ({7000 + i}, {window}, 15, 3) ON CONFLICT DO NOTHING");
        }
        var created = Assert.Single(await JamDetector.RunOnceAsync(db, now));
        Assert.Equal(ReportType.TRAFFIC_JAM, created.Type);
        Assert.Equal("STANDSTILL", created.Subtype);
        Assert.Equal(JamDetector.SystemDeviceId, created.DeviceId);

        // Still jammed 10 minutes later: the same report is kept alive, not duplicated.
        var later = now.AddMinutes(10);
        var again = Assert.Single(await JamDetector.RunOnceAsync(db, later));
        Assert.Equal(created.Id, again.Id);
        Assert.Equal(later + JamDetector.ReportLife, again.ExpiresAt);
        var auto = await db.Reports.Where(r => r.Type == ReportType.TRAFFIC_JAM && r.DeviceId == JamDetector.SystemDeviceId).ToListAsync();
        Assert.Single(auto, r => r.Location.Y > 4.8);
    }
}
