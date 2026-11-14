using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Profile;
using RoadPulse.Api.Modules.Shares;
using RoadPulse.Api.Modules.Traffic;

namespace RoadPulse.Api.Tests;

public class ProfileRulesTests
{
    [Fact]
    public void PointsLevelsAndBadges()
    {
        var s = new ProfileStats(Reports: 12, ConfirmationsReceived: 10, Votes: 30, Removed: 1);
        Assert.Equal(12 * 10 + 10 * 5 + 30 * 2 - 20, ProfileRules.Points(s));
        Assert.Equal(0, ProfileRules.Points(new ProfileStats(0, 0, 0, 3))); // never below zero
        Assert.Equal("ROAD_SCOUT", ProfileRules.Levels[ProfileRules.LevelIndex(210)].Name);
        Assert.Equal("LEARNER", ProfileRules.Levels[ProfileRules.LevelIndex(0)].Name);
        Assert.Equal(["FIRST_REPORT", "REPORTER_10", "HELPFUL_10", "CHECKER_25"], ProfileRules.Badges(s));
    }

    [Theory]
    [InlineData(" Ahmad ", "Ahmad", null)]
    [InlineData("سارة", "سارة", null)]
    [InlineData("", null, null)]
    [InlineData("x", null, "error")]
    [InlineData("<script>", null, "error")]
    public void Nicknames(string raw, string? value, string? error)
    {
        var (v, e) = ProfileRules.CleanNickname(raw);
        Assert.Equal(value, v);
        Assert.Equal(error is null, e is null);
    }
}

public class TrafficRulesTests
{
    [Fact]
    public void LevelsAndWindows()
    {
        Assert.Equal(TrafficLevel.FREE, TrafficRules.Level(60, 70));
        Assert.Equal(TrafficLevel.SLOW, TrafficRules.Level(35, 70));
        Assert.Equal(TrafficLevel.JAM, TrafficRules.Level(10, 70));
        var t = new DateTimeOffset(2026, 11, 15, 8, 7, 31, TimeSpan.Zero);
        Assert.Equal(new DateTimeOffset(2026, 11, 15, 8, 5, 0, TimeSpan.Zero), TrafficRules.WindowStart(t));
        Assert.NotNull(TrafficRules.Validate(new TrafficSegmentRequest([new(3, 101, 0, 10)])));
        var long5 = Enumerable.Range(0, 5).Select(i => new TrafficPoint(3, 101, i * 60, 10)).ToList(); // 240 s
        Assert.NotNull(TrafficRules.Validate(new TrafficSegmentRequest(long5)));
    }

    [Fact]
    public void ParsesARealTraceAttributesAnswer()
    {
        // Real answer from Valhalla for 20 points along a KL road (see the test data file).
        var json = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "valhalla_trace_attributes.json"));
        var points = Enumerable.Range(0, 20).Select(i => new TrafficPoint(0, 0, i * 5, 8.0)).ToList();
        var edges = ValhallaMatcher.Parse(json, points);
        Assert.NotEmpty(edges);
        Assert.All(edges, e =>
        {
            Assert.True(e.Line.Count >= 2);
            Assert.Equal(28.8, e.SpeedKph);   // 8 m/s
            Assert.True(e.ExpectedKph > 0);
            Assert.InRange(e.Line[0].Lat, 3.0, 3.3);
        });
    }
}

/// <summary>Pretends every segment drove on two known edges at the phone's speed.</summary>
file sealed class FakeMatcher : IMapMatcher
{
    public Task<IReadOnlyList<MatchedEdge>> MatchAsync(IReadOnlyList<TrafficPoint> points, CancellationToken ct)
    {
        var kph = points.Average(p => p.SpeedMps ?? 0) * 3.6;
        IReadOnlyList<MatchedEdge> edges =
        [
            new(901, 1, 60, [(3.10, 101.60), (3.10, 101.61)], kph),
            new(902, 1, 60, [(3.10, 101.61), (3.10, 101.62)], kph),
        ];
        return Task.FromResult(edges);
    }
}

[Collection("database")]
public class CommunityApiTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };

    private static async Task<HttpClient> DeviceClient(ApiFactory api)
    {
        var client = await api.ClientAsync();
        var reg = await (await client.PostAsync("/v1/devices", null)).Content.ReadFromJsonAsync<DeviceRegistration>(Json);
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", reg!.Token);
        return client;
    }

    [Fact]
    public async Task ProfileCountsReportsAndConfirmations()
    {
        using var api = new ApiFactory();
        var me = await DeviceClient(api);
        var other = await DeviceClient(api);
        var created = await me.PostAsJsonAsync("/v1/reports", new { type = "HAZARD", lat = 4.51, lon = 102.51 });
        var report = (await created.Content.ReadFromJsonAsync<RoadPulse.Api.Modules.Reports.CreateReportResponse>(Json))!.Report;
        (await other.PostAsJsonAsync($"/v1/reports/{report.Id}/votes", new { stillThere = true })).EnsureSuccessStatusCode();

        var p = (await me.GetFromJsonAsync<ProfileDto>("/v1/me", Json))!;
        Assert.Equal(new ProfileStats(1, 1, 0, 0), p.Stats);
        Assert.Equal(15, p.Points);
        Assert.Equal("LEARNER", p.Level);
        Assert.Equal(100, p.NextLevelPoints);
        Assert.Contains("FIRST_REPORT", p.Badges);
        var voter = (await other.GetFromJsonAsync<ProfileDto>("/v1/me", Json))!;
        Assert.Equal(2, voter.Points);

        var named = await me.PutAsJsonAsync("/v1/me", new { nickname = "Ahmad" });
        Assert.Equal("Ahmad", (await named.Content.ReadFromJsonAsync<ProfileDto>(Json))!.Nickname);
        Assert.Equal(HttpStatusCode.BadRequest, (await me.PutAsJsonAsync("/v1/me", new { nickname = "<b>" })).StatusCode);
        var anonymous = await api.ClientAsync();
        Assert.Equal(HttpStatusCode.Unauthorized, (await anonymous.GetAsync("/v1/me")).StatusCode);
    }

    [Fact]
    public async Task ShareLinkLifecycle()
    {
        using var api = new ApiFactory();
        var me = await DeviceClient(api);
        var start = await me.PostAsJsonAsync("/v1/shares", new
        {
            destination = "M City", destLat = 3.1579, destLon = 101.7116, lat = 3.139, lon = 101.687,
            etaUnixMs = api.Clock.GetUtcNow().AddMinutes(20).ToUnixTimeMilliseconds(), name = "Ahmad",
        });
        Assert.Equal(HttpStatusCode.Created, start.StatusCode);
        var share = (await start.Content.ReadFromJsonAsync<CreateShareResponse>(Json))!;
        Assert.EndsWith("/s/" + share.Id, share.Url);
        Assert.True(share.Id.Length >= 20);

        var viewer = await api.ClientAsync(); // anyone with the link, no token
        var view = (await viewer.GetFromJsonAsync<ShareView>($"/v1/shares/{share.Id}", Json))!;
        Assert.Equal("M City", view.Destination);
        Assert.Equal(ShareState.DRIVING, view.State);
        var page = await viewer.GetAsync($"/s/{share.Id}");
        Assert.Equal("text/html", page.Content.Headers.ContentType!.MediaType);
        Assert.Contains("script-src 'self'", page.Headers.GetValues("Content-Security-Policy").Single());

        // Only the owner can update it.
        var stranger = await DeviceClient(api);
        var upd = new { lat = 3.15, lon = 101.70, etaUnixMs = 0L, state = "ARRIVED" };
        Assert.Equal(HttpStatusCode.NotFound, (await stranger.PutAsJsonAsync($"/v1/shares/{share.Id}", upd)).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent, (await me.PutAsJsonAsync($"/v1/shares/{share.Id}", upd)).StatusCode);
        Assert.Equal(ShareState.ARRIVED, (await viewer.GetFromJsonAsync<ShareView>($"/v1/shares/{share.Id}", Json))!.State);

        // Ends 30 minutes after arrival.
        api.Clock.Advance(TimeSpan.FromMinutes(31));
        Assert.Equal(HttpStatusCode.NotFound, (await viewer.GetAsync($"/v1/shares/{share.Id}")).StatusCode);
    }

    [Fact]
    public async Task StoppingEndsTheLinkAtOnce()
    {
        using var api = new ApiFactory();
        var me = await DeviceClient(api);
        var share = (await (await me.PostAsJsonAsync("/v1/shares", new
        {
            destination = "KLCC", destLat = 3.158, destLon = 101.712, lat = 3.1, lon = 101.6, etaUnixMs = 0L,
        })).Content.ReadFromJsonAsync<CreateShareResponse>(Json))!;
        Assert.Equal(HttpStatusCode.NoContent, (await me.DeleteAsync($"/v1/shares/{share.Id}")).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, (await me.GetAsync($"/v1/shares/{share.Id}")).StatusCode);
    }

    private static object Segment(double speedMps) => new
    {
        points = Enumerable.Range(0, 6).Select(i => new { lat = 3.10, lon = 101.60 + i * 0.004, t = i * 20, speedMps }).ToArray(),
    };

    [Fact]
    public async Task TrafficShowsOnlyAfterThreeSegments()
    {
        using var api = new ApiFactory { ExtraServices = s => { s.RemoveAll<IMapMatcher>(); s.AddSingleton<IMapMatcher, FakeMatcher>(); } };
        var c = await api.ClientAsync(); // anonymous: no device token is needed or used
        const string box = "/v1/traffic?minLat=3.05&minLon=101.55&maxLat=3.15&maxLon=101.65";

        Assert.Equal(HttpStatusCode.Accepted, (await c.PostAsJsonAsync("/v1/traffic/samples", Segment(3))).StatusCode);
        Assert.Equal(HttpStatusCode.Accepted, (await c.PostAsJsonAsync("/v1/traffic/samples", Segment(3))).StatusCode);
        Assert.Empty((await c.GetFromJsonAsync<List<TrafficLineDto>>(box, Json))!); // only 2 segments: hidden

        await c.PostAsJsonAsync("/v1/traffic/samples", Segment(3));
        var lines = (await c.GetFromJsonAsync<List<TrafficLineDto>>(box, Json))!;
        Assert.Equal(2, lines.Count);
        Assert.All(lines, l => Assert.Equal(TrafficLevel.JAM, l.Level)); // ~11 km/h where 60 is normal
        Assert.Equal(11, lines[0].SpeedKph);

        // 16 minutes later it is no longer live.
        api.Clock.Advance(TimeSpan.FromMinutes(16));
        Assert.Empty((await c.GetFromJsonAsync<List<TrafficLineDto>>(box, Json))!);
        Assert.Equal(HttpStatusCode.BadRequest, (await c.GetAsync("/v1/traffic?minLat=1&minLon=100&maxLat=5&maxLon=104")).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await c.PostAsJsonAsync("/v1/traffic/samples", new { points = Array.Empty<object>() })).StatusCode);
    }
}
