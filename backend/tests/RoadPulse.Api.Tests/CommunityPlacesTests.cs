using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.CommunityPlaces;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Moderation;
using Microsoft.Extensions.DependencyInjection;

namespace RoadPulse.Api.Tests;

/// <summary>"Add a missing place": add, search (own pending + approved), moderation, privacy.</summary>
[Collection("database")]
public class CommunityPlacesTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };

    private static async Task<HttpClient> Device(ApiFactory api)
    {
        var client = await api.ClientAsync();
        var reg = (await (await client.PostAsync("/v1/devices", null)).Content.ReadFromJsonAsync<DeviceRegistration>(Json))!;
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", reg.Token);
        return client;
    }

    private static async Task<List<CommunityPlaceDto>> Search(HttpClient c, string q) =>
        (await c.GetFromJsonAsync<List<CommunityPlaceDto>>($"/v1/places/community/search?q={Uri.EscapeDataString(q)}&lat=3.16&lon=101.72", Json))!;

    [Fact]
    public void SearchKeyIgnoresSpacesDashesAndCase()
    {
        Assert.Equal("mcityampang", CommunityPlaceEndpoints.Key("M-City  Ampang"));
        Assert.Equal("klinikسارة", CommunityPlaceEndpoints.Key("Klinik سارة!"));
    }

    [Fact]
    public async Task AddedPlaceIsPrivateUntilApproved()
    {
        using var api = new ApiFactory();
        var me = await Device(api);
        var other = await Device(api);

        var res = await me.PostAsJsonAsync("/v1/places/community", new
        {
            name = "M City Lobby 2", kind = "ENTRANCE", lat = 3.1600, lon = 101.7200, note = "Drop-off at the side gate",
        });
        Assert.Equal(HttpStatusCode.Created, res.StatusCode);
        var added = (await res.Content.ReadFromJsonAsync<CommunityPlaceDto>(Json))!;
        Assert.Equal(CommunityPlaceStatus.Pending, added.Status);
        Assert.True(added.Own);

        // "mcity" finds "M City Lobby 2" for the person who added it, not for others yet.
        Assert.Single(await Search(me, "mcity"));
        Assert.Empty(await Search(other, "mcity"));

        // The same name at the same spot is not added twice.
        var again = await other.PostAsJsonAsync("/v1/places/community", new { name = "m-city lobby 2", kind = "ENTRANCE", lat = 3.16001, lon = 101.72001 });
        Assert.Equal(HttpStatusCode.OK, again.StatusCode);

        // A moderator approves it: now everyone finds it.
        var mod = await api.ClientAsync();
        mod.DefaultRequestHeaders.Add(ModeratorAuthHandler.Header, ApiFactory.ModeratorKey);
        var queue = (await mod.GetFromJsonAsync<List<PlaceReviewItem>>("/v1/moderation/places", Json))!;
        Assert.Contains(queue, q => q.Place.Id == added.Id);
        Assert.Equal(HttpStatusCode.NoContent,
            (await mod.PostAsJsonAsync($"/v1/moderation/places/{added.Id}/approve", new { reason = "Checked on the map" })).StatusCode);
        var found = Assert.Single(await Search(other, "lobby"));
        Assert.False(found.Own);
        Assert.Equal("Drop-off at the side gate", found.Note);

        // Far away: not found.
        var far = (await other.GetFromJsonAsync<List<CommunityPlaceDto>>("/v1/places/community/search?q=mcity&lat=5.4&lon=100.3", Json))!;
        Assert.Empty(far);

        // Only the owner can remove it, and deleting your data removes it too.
        Assert.Equal(HttpStatusCode.NotFound, (await other.DeleteAsync($"/v1/places/community/{added.Id}")).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent, (await me.DeleteAsync("/v1/me")).StatusCode);
        Assert.Empty(await Search(other, "lobby"));
    }

    [Fact]
    public async Task RejectedPlacesStayHiddenAndBadInputIsRefused()
    {
        using var api = new ApiFactory();
        var me = await Device(api);
        Assert.Equal(HttpStatusCode.BadRequest, (await me.PostAsJsonAsync("/v1/places/community", new { name = "-", kind = "SHOP", lat = 3.16, lon = 101.72 })).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await me.GetAsync("/v1/places/community/search?q=a&lat=3.16&lon=101.72")).StatusCode);

        var added = (await (await me.PostAsJsonAsync("/v1/places/community", new { name = "Fake Shop", kind = "SHOP", lat = 3.161, lon = 101.721 }))
            .Content.ReadFromJsonAsync<CommunityPlaceDto>(Json))!;
        var mod = await api.ClientAsync();
        mod.DefaultRequestHeaders.Add(ModeratorAuthHandler.Header, ApiFactory.ModeratorKey);
        await mod.PostAsJsonAsync($"/v1/moderation/places/{added.Id}/reject", new { reason = "Not a real place" });
        Assert.Empty(await Search(me, "fake"));
        var export = await me.GetStringAsync("/v1/me/export");
        Assert.Contains("Fake Shop", export);
    }
}

/// <summary>Overture places: import from the export file and search by name near a position.</summary>
[Collection("database")]
public class DirectoryPlacesTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    [Fact]
    public async Task ImportThenSearchNearbyByName()
    {
        using var api = new ApiFactory();
        using (var scope = api.Services.CreateScope())
        {
            var db = scope.ServiceProvider.GetRequiredService<RoadPulseDb>();
            var n = await RoadPulse.Api.Modules.Directory.DirectoryImporter.ImportAsync(db,
                Path.Combine(AppContext.BaseDirectory, "data", "overture-sample.jsonl"), Microsoft.Extensions.Logging.Abstractions.NullLogger.Instance);
            Assert.Equal(4, n); // the duplicate id and the name without letters are skipped
        }
        var c = await api.ClientAsync(); // public: no device token needed
        var hits = (await c.GetFromJsonAsync<List<RoadPulse.Api.Modules.Directory.DirectoryPlaceDto>>(
            "/v1/places/search?q=mcity&lat=3.16&lon=101.72", Json))!;
        // Names starting with "mcity" first, then the gym; Miri is more than 100 km away.
        Assert.Equal(["M-City Jalan Ampang", "M City Dental Clinic", "Sky Gym at M-City Residence"], hits.Select(h => h.Name).ToList());
        Assert.Equal("shopping_mall", hits[0].Category);
        Assert.Equal(HttpStatusCode.BadRequest, (await c.GetAsync("/v1/places/search?q=m&lat=3.16&lon=101.72")).StatusCode);
    }
}

public class DirectoryDedupeTests
{
    private static RoadPulse.Api.Modules.Directory.DirectoryImporter.Row R(string id, string name, double lat, double lon, float conf, string? cat = null) =>
        new(id, name, cat, null, lat, lon, conf);

    [Fact]
    public void SameNameNearbyOrNameWithASuffixIsKeptOnceTheMostConfident()
    {
        var rows = RoadPulse.Api.Modules.Directory.DirectoryImporter.Dedupe([
            R("1", "M-City Jalan Ampang", 3.1593, 101.7472, 0.6f),
            R("2", "m city jalan ampang", 3.1596, 101.7473, 0.9f, "shopping_mall"),   // same name, ~35 m: duplicate
            R("3", "Crowne Plaza Kuala Lumpur City Centre", 3.16309, 101.71386, 0.8f, "hotel"),
            R("4", "Crowne Plaza Kuala Lumpur City Centre, an IHG Hotel", 3.16309, 101.71386, 0.7f),
            R("5", "M-City Jalan Ampang", 3.20, 101.7472, 0.5f),                       // same name 4.5 km away: another place
            R("6", "Klinik A", 3.1593, 101.7472, 0.5f),
        ]);
        Assert.Equal(["2", "3", "5", "6"], rows.Select(r => r.Id).Order().ToList());
    }

    [Fact]
    public void TheKeptRecordTakesACategoryFromItsDuplicate()
    {
        var rows = RoadPulse.Api.Modules.Directory.DirectoryImporter.Dedupe([
            R("1", "Sky Gym", 3.1, 101.7, 0.9f),
            R("2", "Sky Gym", 3.1, 101.7, 0.4f, "gym"),
        ]);
        Assert.Equal("gym", Assert.Single(rows).Category);
    }
}
