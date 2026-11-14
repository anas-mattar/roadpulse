using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Reports;

namespace RoadPulse.Api.Tests;

/// <summary>End-to-end tests of the v1 API on a real PostGIS database. Each test uses its own map area.</summary>
[Collection("database")]
public class ReportsApiTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };
    private static int _area;

    // A fresh ~1 km square in Kuala Lumpur for each test, so tests do not see each other's reports.
    private static (double lat, double lon) NewArea()
    {
        var n = Interlocked.Increment(ref _area);
        return (3.0 + n * 0.02, 101.5 + n * 0.02);
    }

    private static string Bbox(double lat, double lon) =>
        FormattableString.Invariant($"{lon - 0.01},{lat - 0.01},{lon + 0.01},{lat + 0.01}");

    private static async Task<HttpClient> DeviceClient(ApiFactory api)
    {
        var client = await api.ClientAsync();
        var reg = await (await client.PostAsync("/v1/devices", null)).Content.ReadFromJsonAsync<DeviceRegistration>(Json);
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", reg!.Token);
        return client;
    }

    private static Task<HttpResponseMessage> Post(HttpClient c, string type, double lat, double lon, string? key = null)
    {
        var req = new HttpRequestMessage(HttpMethod.Post, "/v1/reports")
        {
            Content = JsonContent.Create(new { type, lat, lon }),
        };
        if (key != null) req.Headers.Add("Idempotency-Key", key);
        return c.SendAsync(req);
    }

    private static async Task<List<ReportDto>> List(HttpClient c, double lat, double lon) =>
        (await c.GetFromJsonAsync<List<ReportDto>>($"/v1/reports?bbox={Bbox(lat, lon)}", Json))!;

    [Fact]
    public async Task CreateAndListReportsInsideTheBox()
    {
        using var api = new ApiFactory();
        var c = await DeviceClient(api);
        var (lat, lon) = NewArea();

        var res = await Post(c, "CRASH", lat, lon);
        Assert.Equal(HttpStatusCode.Created, res.StatusCode);
        var created = (await res.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!;
        Assert.False(created.Merged);
        Assert.Equal(lat, created.Report.Lat, 6);

        Assert.Single(await List(c, lat, lon));
        Assert.Empty(await List(c, lat + 0.05, lon + 0.05)); // another area
    }

    [Fact]
    public async Task WritesNeedADeviceTokenButReadsDoNot()
    {
        using var api = new ApiFactory();
        var anonymous = await api.ClientAsync();
        var (lat, lon) = NewArea();
        Assert.Equal(HttpStatusCode.Unauthorized, (await Post(anonymous, "POLICE", lat, lon)).StatusCode);
        anonymous.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "not-a-real-token-but-long-enough");
        Assert.Equal(HttpStatusCode.Unauthorized, (await Post(anonymous, "POLICE", lat, lon)).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await anonymous.GetAsync($"/v1/reports?bbox={Bbox(lat, lon)}")).StatusCode);
    }

    [Fact]
    public async Task InvalidInputGivesProblemDetails()
    {
        using var api = new ApiFactory();
        var c = await DeviceClient(api);
        var res = await Post(c, "HAZARD", 95, 101.6);
        Assert.Equal(HttpStatusCode.BadRequest, res.StatusCode);
        Assert.Equal("application/problem+json", res.Content.Headers.ContentType?.MediaType);
        Assert.Contains("lat", await res.Content.ReadAsStringAsync());

        var unknownType = await Post(c, "UFO", 3.1, 101.6);
        Assert.Equal(HttpStatusCode.BadRequest, unknownType.StatusCode);
        Assert.Contains("TRAFFIC_JAM", await unknownType.Content.ReadAsStringAsync());

        var big = await c.GetAsync("/v1/reports?bbox=100,2,102,4");
        Assert.Equal(HttpStatusCode.BadRequest, big.StatusCode);
        Assert.Contains("too large", await big.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task SameReportNearbyIsMergedAsAConfirmation()
    {
        using var api = new ApiFactory();
        var first = await DeviceClient(api);
        var second = await DeviceClient(api);
        var (lat, lon) = NewArea();

        var a = (await (await Post(first, "HAZARD", lat, lon)).Content.ReadFromJsonAsync<CreateReportResponse>(Json))!;
        var res = await Post(second, "HAZARD", lat + 0.0005, lon); // ~55 m away
        Assert.Equal(HttpStatusCode.OK, res.StatusCode);
        var b = (await res.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!;
        Assert.True(b.Merged);
        Assert.Equal(a.Report.Id, b.Report.Id);
        Assert.Equal(1, b.Report.Confirmations);

        // A different type, or further away, is a new report.
        Assert.Equal(HttpStatusCode.Created, (await Post(second, "POLICE", lat, lon)).StatusCode);
        Assert.Equal(HttpStatusCode.Created, (await Post(second, "HAZARD", lat + 0.003, lon)).StatusCode); // ~330 m
        Assert.Equal(3, (await List(first, lat, lon)).Count);
    }

    [Fact]
    public async Task RetryWithSameIdempotencyKeyCreatesOneReport()
    {
        using var api = new ApiFactory();
        var c = await DeviceClient(api);
        var (lat, lon) = NewArea();
        var r1 = await Post(c, "ROAD_CLOSED", lat, lon, key: "retry-123");
        var r2 = await Post(c, "ROAD_CLOSED", lat, lon, key: "retry-123");
        Assert.Equal(HttpStatusCode.Created, r1.StatusCode);
        Assert.Equal(HttpStatusCode.OK, r2.StatusCode);
        var id1 = (await r1.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report.Id;
        var id2 = (await r2.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report.Id;
        Assert.Equal(id1, id2);
        Assert.Single(await List(c, lat, lon));
    }

    [Fact]
    public async Task TwoNotThereVotesRemoveAReportAndReportersCannotVoteOnTheirOwn()
    {
        using var api = new ApiFactory();
        var reporter = await DeviceClient(api);
        var v1 = await DeviceClient(api);
        var v2 = await DeviceClient(api);
        var (lat, lon) = NewArea();
        var id = (await (await Post(reporter, "POLICE", lat, lon)).Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report.Id;

        Assert.Equal(HttpStatusCode.Conflict, (await reporter.PostAsJsonAsync($"/v1/reports/{id}/votes", new { stillThere = true })).StatusCode);
        Assert.Equal(HttpStatusCode.OK, (await v1.PostAsJsonAsync($"/v1/reports/{id}/votes", new { stillThere = false })).StatusCode);
        Assert.Single(await List(reporter, lat, lon)); // one dismissal is not enough
        // Voting twice the same way counts once.
        await v1.PostAsJsonAsync($"/v1/reports/{id}/votes", new { stillThere = false });
        Assert.Single(await List(reporter, lat, lon));
        await v2.PostAsJsonAsync($"/v1/reports/{id}/votes", new { stillThere = false });
        Assert.Empty(await List(reporter, lat, lon));
        Assert.Equal(HttpStatusCode.NotFound, (await v2.PostAsJsonAsync($"/v1/reports/{id}/votes", new { stillThere = true })).StatusCode);
    }

    [Fact]
    public async Task ReportsExpireAndConfirmationsKeepThemAlive()
    {
        using var api = new ApiFactory();
        var reporter = await DeviceClient(api);
        var voter = await DeviceClient(api);
        var (lat, lon) = NewArea();
        var jam = (await (await Post(reporter, "TRAFFIC_JAM", lat, lon)).Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report; // 30 min TTL
        await Post(reporter, "CRASH", lat + 0.004, lon); // 60 min TTL, no votes

        api.Clock.Advance(TimeSpan.FromMinutes(25));
        var voted = await voter.PostAsJsonAsync($"/v1/reports/{jam.Id}/votes", new { stillThere = true });
        Assert.Equal(api.Clock.GetUtcNow().AddMinutes(15), (await voted.Content.ReadFromJsonAsync<ReportDto>(Json))!.ExpiresAt);

        api.Clock.Advance(TimeSpan.FromMinutes(10)); // 35 min: jam kept alive by the vote
        Assert.Equal(2, (await List(reporter, lat, lon)).Count);
        api.Clock.Advance(TimeSpan.FromMinutes(10)); // 45 min: jam expired, crash still active
        Assert.Equal(["CRASH"], (await List(reporter, lat, lon)).Select(r => r.Type.ToString()));
        api.Clock.Advance(TimeSpan.FromMinutes(20)); // 65 min: everything expired
        Assert.Empty(await List(reporter, lat, lon));
    }

    [Fact]
    public async Task ReportingTooFastIsRateLimitedPerDevice()
    {
        using var api = new ApiFactory { ReportsPer10Minutes = 3 };
        var c = await DeviceClient(api);
        var other = await DeviceClient(api);
        var (lat, lon) = NewArea();
        for (var i = 0; i < 3; i++)
            Assert.Equal(HttpStatusCode.Created, (await Post(c, "HAZARD", lat + i * 0.003, lon)).StatusCode);
        Assert.Equal(HttpStatusCode.TooManyRequests, (await Post(c, "HAZARD", lat + 0.009, lon)).StatusCode);
        Assert.Equal(HttpStatusCode.Created, (await Post(other, "HAZARD", lat + 0.009, lon)).StatusCode); // other device unaffected
    }

    [Fact]
    public async Task HealthAndOpenApiAreServed()
    {
        using var api = new ApiFactory();
        var c = await api.ClientAsync();
        Assert.Equal(HttpStatusCode.OK, (await c.GetAsync("/health")).StatusCode);
        var doc = await c.GetStringAsync("/openapi/v1.json");
        Assert.Contains("/v1/reports", doc);
        Assert.Contains("/v1/devices", doc);
    }

    [Fact]
    public async Task DetailsAndClosureDurationAreStoredAndValidated()
    {
        using var api = new ApiFactory();
        var c = await DeviceClient(api);
        var (lat, lon) = NewArea();

        var flood = await c.PostAsJsonAsync("/v1/reports", new { type = "HAZARD", lat, lon, subtype = "FLOOD" });
        Assert.Equal(HttpStatusCode.Created, flood.StatusCode);
        Assert.Equal("FLOOD", (await flood.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report.Subtype);

        var closure = await c.PostAsJsonAsync("/v1/reports", new { type = "ROAD_CLOSED", lat = lat + 0.005, lon, subtype = "LANE_BLOCKED", durationMinutes = 180 });
        var closed = (await closure.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report;
        Assert.Equal(TimeSpan.FromHours(3), closed.ExpiresAt - closed.CreatedAt);

        var allDay = await c.PostAsJsonAsync("/v1/reports", new { type = "ROAD_CLOSED", lat = lat + 0.008, lon, durationMinutes = 1440 });
        var day = (await allDay.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report;
        Assert.Equal(TimeSpan.FromHours(24), day.ExpiresAt - day.CreatedAt); // capped at the max lifetime (4 x 6 h)

        Assert.Equal(HttpStatusCode.BadRequest, (await c.PostAsJsonAsync("/v1/reports", new { type = "POLICE", lat, lon, subtype = "FLOOD" })).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await c.PostAsJsonAsync("/v1/reports", new { type = "POLICE", lat, lon, durationMinutes = 60 })).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await c.PostAsJsonAsync("/v1/reports", new { type = "ROAD_CLOSED", lat, lon, durationMinutes = 5 })).StatusCode);

        var listed = await List(c, lat, lon);
        Assert.Contains(listed, r => r.Subtype == "FLOOD");
    }
}

[CollectionDefinition("database", DisableParallelization = true)]
public class DatabaseCollection;
