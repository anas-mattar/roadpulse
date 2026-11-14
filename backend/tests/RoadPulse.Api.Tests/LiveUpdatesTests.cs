using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Http.Connections;
using Microsoft.AspNetCore.SignalR;
using Microsoft.AspNetCore.SignalR.Client;
using Microsoft.Extensions.DependencyInjection;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Live;
using RoadPulse.Api.Modules.Moderation;
using RoadPulse.Api.Modules.Reports;

namespace RoadPulse.Api.Tests;

/// <summary>Live updates over SignalR: subscribers get changes for their map area only.</summary>
[Collection("database")]
public class LiveUpdatesTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };
    private static readonly TimeSpan Wait = TimeSpan.FromSeconds(5);
    private static int _area;

    // Its own area range (Sabah) so other test classes never send events here.
    private static (double lat, double lon) NewArea()
    {
        var n = Interlocked.Increment(ref _area);
        return (5.0 + n * 0.3, 116.0 + n * 0.3);
    }

    /// <summary>A live client watching roughly 1 km around a point, with every event it receives.</summary>
    private sealed class Watcher : IAsyncDisposable
    {
        public HubConnection Connection { get; }
        public List<ReportDto> Upserts { get; } = new();
        public List<Guid> Removals { get; } = new();
        private readonly SemaphoreSlim _events = new(0);

        public Watcher(ApiFactory api)
        {
            Connection = new HubConnectionBuilder()
                .WithUrl(new Uri(api.Server.BaseAddress, ReportsHub.Path), o =>
                {
                    o.HttpMessageHandlerFactory = _ => api.Server.CreateHandler();
                    o.Transports = HttpTransportType.LongPolling;
                })
                .AddJsonProtocol(o => o.PayloadSerializerOptions.Converters.Add(new JsonStringEnumConverter()))
                .Build();
            Connection.On<ReportDto>(ReportsHub.Upserted, r => { lock (Upserts) Upserts.Add(r); _events.Release(); });
            Connection.On<Guid>(ReportsHub.Removed, id => { lock (Removals) Removals.Add(id); _events.Release(); });
        }

        public async Task WatchAsync(double lat, double lon)
        {
            if (Connection.State == HubConnectionState.Disconnected) await Connection.StartAsync();
            await Connection.InvokeAsync<int>("SubscribeArea", lon - 0.01, lat - 0.01, lon + 0.01, lat + 0.01);
        }

        public Task<bool> NextEventAsync(TimeSpan? timeout = null) => _events.WaitAsync(timeout ?? Wait);

        public ValueTask DisposeAsync() => Connection.DisposeAsync();
    }

    private static async Task<HttpClient> Device(ApiFactory api)
    {
        var client = await api.ClientAsync();
        var reg = (await (await client.PostAsync("/v1/devices", null)).Content.ReadFromJsonAsync<DeviceRegistration>(Json))!;
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", reg.Token);
        return client;
    }

    private static async Task<ReportDto> CreateReport(HttpClient c, double lat, double lon)
    {
        var res = await c.PostAsJsonAsync("/v1/reports", new { type = "CRASH", lat, lon });
        res.EnsureSuccessStatusCode();
        return (await res.Content.ReadFromJsonAsync<CreateReportResponse>(Json))!.Report;
    }

    [Fact]
    public async Task NewReportsReachWatchersOfThatAreaOnly()
    {
        using var api = new ApiFactory();
        var reporter = await Device(api);
        var (lat, lon) = NewArea();
        var (farLat, farLon) = NewArea();
        await using var near = new Watcher(api);
        await using var far = new Watcher(api);
        await near.WatchAsync(lat, lon);
        await far.WatchAsync(farLat, farLon);

        var report = await CreateReport(reporter, lat, lon);

        Assert.True(await near.NextEventAsync());
        Assert.Equal(report.Id, Assert.Single(near.Upserts).Id);
        Assert.False(await far.NextEventAsync(TimeSpan.FromMilliseconds(500)));
    }

    [Fact]
    public async Task ConfirmationsAndRemovalsArePushed()
    {
        using var api = new ApiFactory();
        var reporter = await Device(api);
        var (lat, lon) = NewArea();
        await using var watcher = new Watcher(api);
        await watcher.WatchAsync(lat, lon);
        var report = await CreateReport(reporter, lat, lon);
        Assert.True(await watcher.NextEventAsync());

        // Another driver says "still there": updated counts are pushed.
        var other = await Device(api);
        await other.PostAsJsonAsync($"/v1/reports/{report.Id}/votes", new { stillThere = true });
        Assert.True(await watcher.NextEventAsync());
        Assert.Equal(1, watcher.Upserts[^1].Confirmations);

        // A moderator removes it: a removal is pushed.
        var mod = await api.ClientAsync();
        mod.DefaultRequestHeaders.Add(ModeratorAuthHandler.Header, ApiFactory.ModeratorKey);
        await mod.PostAsJsonAsync($"/v1/moderation/reports/{report.Id}/remove", new { reason = "Duplicate of another crash" });
        Assert.True(await watcher.NextEventAsync());
        Assert.Equal(report.Id, Assert.Single(watcher.Removals));
    }

    [Fact]
    public async Task ThreeFlagsPushARemoval()
    {
        using var api = new ApiFactory();
        var reporter = await Device(api);
        var (lat, lon) = NewArea();
        var report = await CreateReport(reporter, lat, lon);
        await using var watcher = new Watcher(api);
        await watcher.WatchAsync(lat, lon);

        for (var i = 0; i < 3; i++)
            await (await Device(api)).PostAsJsonAsync($"/v1/reports/{report.Id}/flags", new { reason = "FAKE" });

        Assert.True(await watcher.NextEventAsync());
        Assert.Equal(report.Id, Assert.Single(watcher.Removals));
        Assert.Empty(watcher.Upserts); // flags below the limit send nothing
    }

    [Fact]
    public async Task MovingTheMapSwitchesArea()
    {
        using var api = new ApiFactory();
        var reporter = await Device(api);
        var (oldLat, oldLon) = NewArea();
        var (newLat, newLon) = NewArea();
        await using var watcher = new Watcher(api);
        await watcher.WatchAsync(oldLat, oldLon);
        await watcher.WatchAsync(newLat, newLon);

        await CreateReport(reporter, oldLat, oldLon);
        Assert.False(await watcher.NextEventAsync(TimeSpan.FromMilliseconds(500)));
        await CreateReport(reporter, newLat, newLon);
        Assert.True(await watcher.NextEventAsync());
    }

    [Fact]
    public async Task OversizedOrInvalidAreasAreRejected()
    {
        using var api = new ApiFactory();
        await api.ClientAsync();
        await using var watcher = new Watcher(api);
        await watcher.Connection.StartAsync();
        var tooBig = await Assert.ThrowsAsync<HubException>(() => watcher.Connection.InvokeAsync<int>("SubscribeArea", 101.0, 3.0, 102.0, 3.2));
        Assert.Contains("too large", tooBig.Message);
        await Assert.ThrowsAsync<HubException>(() => watcher.Connection.InvokeAsync<int>("SubscribeArea", 102.0, 3.0, 101.0, 3.2));
        Assert.Equal(36, await watcher.Connection.InvokeAsync<int>("SubscribeArea", 101.05, 3.05, 101.55, 3.55));
    }

    [Theory]
    [InlineData(101.69, 3.14, "tile:1016:31")]
    [InlineData(-0.05, -0.05, "tile:-1:-1")]
    public void TilesUseFloor(double lon, double lat, string expected) => Assert.Equal(expected, ReportsHub.TileFor(lon, lat));
}
