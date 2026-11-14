using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Moderation;
using RoadPulse.Api.Modules.Reports;

namespace RoadPulse.Api.Tests;

/// <summary>Abuse flags, the review queue, moderator actions and the audit log, on a real PostGIS database.</summary>
[Collection("database")]
public class ModerationApiTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };
    private static int _area;

    // Separate area range from ReportsApiTests so the two classes never overlap.
    private static (double lat, double lon) NewArea()
    {
        var n = Interlocked.Increment(ref _area);
        return (1.5 + n * 0.02, 103.0 + n * 0.02);
    }

    private static string Bbox(double lat, double lon) =>
        FormattableString.Invariant($"{lon - 0.01},{lat - 0.01},{lon + 0.01},{lat + 0.01}");

    private static async Task<(HttpClient client, Guid id)> Device(ApiFactory api)
    {
        var client = await api.ClientAsync();
        var reg = (await (await client.PostAsync("/v1/devices", null)).Content.ReadFromJsonAsync<DeviceRegistration>(Json))!;
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", reg.Token);
        return (client, reg.DeviceId);
    }

    private static async Task<HttpClient> Moderator(ApiFactory api)
    {
        var client = await api.ClientAsync();
        client.DefaultRequestHeaders.Add(ModeratorAuthHandler.Header, ApiFactory.ModeratorKey);
        return client;
    }

    private static async Task<ReportDto> CreateReport(HttpClient c, double lat, double lon, string type = "POLICE")
    {
        var res = await c.PostAsJsonAsync("/v1/reports", new { type, lat, lon });
        res.EnsureSuccessStatusCode();
        return (await res.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report;
    }

    private static Task<HttpResponseMessage> Flag(HttpClient c, Guid id, string reason = "FAKE") =>
        c.PostAsJsonAsync($"/v1/reports/{id}/flags", new { reason });

    private static async Task<List<ReportDto>> MapList(HttpClient c, double lat, double lon) =>
        (await c.GetFromJsonAsync<List<ReportDto>>($"/v1/reports?bbox={Bbox(lat, lon)}", Json))!;

    private static async Task<List<QueueItem>> Queue(HttpClient mod) =>
        (await mod.GetFromJsonAsync<List<QueueItem>>("/v1/moderation/queue", Json))!;

    private static async Task<List<AuditDto>> AuditFor(HttpClient mod, Guid target) =>
        (await mod.GetFromJsonAsync<List<AuditDto>>($"/v1/moderation/audit?target={target}", Json))!;

    [Fact]
    public async Task ThreeFlagsHideAReportUntilReviewed()
    {
        using var api = new ApiFactory();
        var (reporter, _) = await Device(api);
        var (lat, lon) = NewArea();
        var report = await CreateReport(reporter, lat, lon);

        for (var i = 0; i < 2; i++)
            Assert.Equal(HttpStatusCode.NoContent, (await Flag((await Device(api)).client, report.Id)).StatusCode);
        Assert.Single(await MapList(reporter, lat, lon)); // two flags: still visible

        var (third, _) = await Device(api);
        await Flag(third, report.Id, "SPAM");
        Assert.Empty(await MapList(reporter, lat, lon)); // hidden for review

        var item = Assert.Single(await Queue(await Moderator(api)), q => q.Report.Id == report.Id);
        Assert.Equal(ReportStatus.PendingReview, item.Status);
        Assert.Equal(3, item.Flags);
        Assert.Equal(2, item.FlagReasons[FlagReason.FAKE]);
        Assert.Equal(1, item.FlagReasons[FlagReason.SPAM]);
        Assert.Equal(HttpStatusCode.NotFound, (await third.PostAsJsonAsync($"/v1/reports/{report.Id}/votes", new { stillThere = true })).StatusCode);
    }

    [Fact]
    public async Task FlagRulesOwnReportRepeatAndBadReason()
    {
        using var api = new ApiFactory();
        var (reporter, _) = await Device(api);
        var (other, _) = await Device(api);
        var (lat, lon) = NewArea();
        var report = await CreateReport(reporter, lat, lon);

        Assert.Equal(HttpStatusCode.Conflict, (await Flag(reporter, report.Id)).StatusCode);
        await Flag(other, report.Id);
        await Flag(other, report.Id); // repeat is accepted but not counted twice
        var item = Assert.Single(await Queue(await Moderator(api)), q => q.Report.Id == report.Id);
        Assert.Equal(1, item.Flags);
        Assert.Equal(ReportStatus.Active, item.Status);

        Assert.Equal(HttpStatusCode.BadRequest, (await Flag(other, report.Id, "BORING")).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, (await Flag(other, Guid.NewGuid())).StatusCode);
        var anonymous = await api.ClientAsync();
        Assert.Equal(HttpStatusCode.Unauthorized, (await Flag(anonymous, report.Id)).StatusCode);
    }

    [Fact]
    public async Task ModerationNeedsAModeratorKey()
    {
        using var api = new ApiFactory();
        var anonymous = await api.ClientAsync();
        Assert.Equal(HttpStatusCode.Unauthorized, (await anonymous.GetAsync("/v1/moderation/queue")).StatusCode);

        var (device, _) = await Device(api); // a valid device token is not enough
        Assert.Equal(HttpStatusCode.Unauthorized, (await device.GetAsync("/v1/moderation/queue")).StatusCode);

        var wrong = await api.ClientAsync();
        wrong.DefaultRequestHeaders.Add(ModeratorAuthHandler.Header, "wrong-key-but-long-enough-000000");
        Assert.Equal(HttpStatusCode.Unauthorized, (await wrong.GetAsync("/v1/moderation/queue")).StatusCode);

        Assert.Equal(HttpStatusCode.OK, (await (await Moderator(api)).GetAsync("/v1/moderation/queue")).StatusCode);
    }

    [Fact]
    public async Task RemoveAndRestoreAreAuditedAndRestoredReportsStayUp()
    {
        using var api = new ApiFactory();
        var mod = await Moderator(api);
        var (reporter, _) = await Device(api);
        var (lat, lon) = NewArea();
        var report = await CreateReport(reporter, lat, lon);

        Assert.Equal(HttpStatusCode.BadRequest, (await mod.PostAsJsonAsync($"/v1/moderation/reports/{report.Id}/remove", new { reason = "" })).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent, (await mod.PostAsJsonAsync($"/v1/moderation/reports/{report.Id}/remove", new { reason = "Fake police report" })).StatusCode);
        Assert.Empty(await MapList(reporter, lat, lon));

        Assert.Equal(HttpStatusCode.NoContent, (await mod.PostAsJsonAsync($"/v1/moderation/reports/{report.Id}/restore", new { reason = "Checked with CCTV" })).StatusCode);
        Assert.Single(await MapList(reporter, lat, lon));

        // After review, new flags no longer hide it automatically.
        for (var i = 0; i < 3; i++) await Flag((await Device(api)).client, report.Id);
        Assert.Single(await MapList(reporter, lat, lon));
        Assert.DoesNotContain(await Queue(mod), q => q.Report.Id == report.Id);

        var audit = await AuditFor(mod, report.Id);
        Assert.Equal(["RESTORE_REPORT", "REMOVE_REPORT"], audit.Select(a => a.Action));
        Assert.All(audit, a => Assert.Equal("alice", a.Moderator));
        Assert.Equal("Fake police report", audit[1].Reason);
        Assert.Equal(HttpStatusCode.NotFound, (await mod.PostAsJsonAsync($"/v1/moderation/reports/{Guid.NewGuid()}/remove", new { reason = "x-y-z" })).StatusCode);
    }

    [Fact]
    public async Task BlockingADeviceStopsItsTokenAndRemovesItsReports()
    {
        using var api = new ApiFactory();
        var mod = await Moderator(api);
        var (spammer, spammerId) = await Device(api);
        var (lat, lon) = NewArea();
        await CreateReport(spammer, lat, lon, "POLICE");
        await CreateReport(spammer, lat + 0.005, lon, "CRASH");

        var res = await mod.PostAsJsonAsync($"/v1/moderation/devices/{spammerId}/block", new { reason = "Mass fake reports" });
        Assert.Equal(HttpStatusCode.NoContent, res.StatusCode);
        Assert.Empty(await MapList(mod, lat, lon));
        Assert.Equal(HttpStatusCode.Unauthorized, (await spammer.PostAsJsonAsync("/v1/reports", new { type = "HAZARD", lat, lon })).StatusCode);

        var summary = (await mod.GetFromJsonAsync<DeviceSummary>($"/v1/moderation/devices/{spammerId}", Json))!;
        Assert.True(summary.Blocked);
        Assert.Equal(2, summary.RemovedReports);
        Assert.Equal(0, summary.ActiveReports);
        Assert.Equal("BLOCK_DEVICE", Assert.Single(await AuditFor(mod, spammerId)).Action);

        Assert.Equal(HttpStatusCode.NoContent, (await mod.PostAsJsonAsync($"/v1/moderation/devices/{spammerId}/unblock", new { reason = "Appeal accepted" })).StatusCode);
        Assert.Equal(HttpStatusCode.Created, (await spammer.PostAsJsonAsync("/v1/reports", new { type = "HAZARD", lat, lon })).StatusCode);
        Assert.Equal(["UNBLOCK_DEVICE", "BLOCK_DEVICE"], (await AuditFor(mod, spammerId)).Select(a => a.Action));
    }

    [Fact]
    public async Task AuditLimitIsValidated()
    {
        using var api = new ApiFactory();
        var mod = await Moderator(api);
        Assert.Equal(HttpStatusCode.BadRequest, (await mod.GetAsync("/v1/moderation/audit?limit=0")).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await mod.GetAsync("/v1/moderation/audit?limit=501")).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await mod.GetAsync("/v1/moderation/audit?limit=5")).StatusCode);
    }

    [Fact]
    public async Task RecentReportsAndLookupShowAnyStatus()
    {
        using var api = new ApiFactory();
        var mod = await Moderator(api);
        var (reporter, reporterId) = await Device(api);
        var (lat, lon) = NewArea();
        var report = await CreateReport(reporter, lat, lon, "CRASH");
        await mod.PostAsJsonAsync($"/v1/moderation/reports/{report.Id}/remove", new { reason = "Testing lookup" });

        var recent = (await mod.GetFromJsonAsync<List<QueueItem>>("/v1/moderation/reports?limit=500", Json))!;
        var item = Assert.Single(recent, i => i.Report.Id == report.Id);
        Assert.Equal(ReportStatus.Removed, item.Status);
        Assert.Equal(reporterId, item.DeviceId);

        var one = (await mod.GetFromJsonAsync<QueueItem>($"/v1/moderation/reports/{report.Id}", Json))!;
        Assert.Equal(ReportStatus.Removed, one.Status);
        Assert.Equal(HttpStatusCode.NotFound, (await mod.GetAsync($"/v1/moderation/reports/{Guid.NewGuid()}")).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await mod.GetAsync("/v1/moderation/reports?limit=0")).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, (await (await api.ClientAsync()).GetAsync("/v1/moderation/reports")).StatusCode);
    }

    [Fact]
    public async Task ModerationPageIsServedWithStrictHeaders()
    {
        using var api = new ApiFactory();
        var client = await api.ClientAsync();
        var redirect = await api.CreateClient(new Microsoft.AspNetCore.Mvc.Testing.WebApplicationFactoryClientOptions { AllowAutoRedirect = false }).GetAsync("/admin");
        Assert.Equal(HttpStatusCode.Redirect, redirect.StatusCode);
        Assert.Equal("/admin/", redirect.Headers.Location!.ToString());

        var page = await client.GetAsync("/admin/");
        Assert.Equal(HttpStatusCode.OK, page.StatusCode);
        Assert.Contains("RoadPulse moderation", await page.Content.ReadAsStringAsync());
        var csp = string.Join(";", page.Headers.GetValues("Content-Security-Policy"));
        Assert.Contains("default-src 'none'", csp);
        Assert.Contains("frame-ancestors 'none'", csp);
        Assert.DoesNotContain("unsafe-inline", csp);
        Assert.Equal("nosniff", string.Join("", page.Headers.GetValues("X-Content-Type-Options")));
        Assert.Equal(HttpStatusCode.OK, (await client.GetAsync("/admin/admin.js")).StatusCode);
        // The page holds no data: the API still needs the key.
        Assert.Equal(HttpStatusCode.Unauthorized, (await client.GetAsync("/v1/moderation/queue")).StatusCode);
    }

    [Fact]
    public async Task ExpiredHiddenReportsLeaveTheQueueButStayFindable()
    {
        using var api = new ApiFactory();
        var mod = await Moderator(api);
        var (reporter, _) = await Device(api);
        var (lat, lon) = NewArea();
        var report = await CreateReport(reporter, lat, lon, "TRAFFIC_JAM"); // 30 min
        for (var i = 0; i < 3; i++) await Flag((await Device(api)).client, report.Id);
        Assert.Contains(await Queue(mod), q => q.Report.Id == report.Id);

        api.Clock.Advance(TimeSpan.FromMinutes(31));
        Assert.DoesNotContain(await Queue(mod), q => q.Report.Id == report.Id);
        var found = (await mod.GetFromJsonAsync<QueueItem>($"/v1/moderation/reports/{report.Id}", Json))!;
        Assert.Equal(ReportStatus.PendingReview, found.Status);
    }
}
