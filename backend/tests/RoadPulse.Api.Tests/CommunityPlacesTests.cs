using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.CommunityPlaces;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Moderation;

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
