using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Directory;
using RoadPulse.Api.Modules.Moderation;
using RoadPulse.Api.Modules.PlaceProblems;

namespace RoadPulse.Api.Tests;

/// <summary>"Report a problem with this place": report, review, the fix in search, and the fix surviving a new import.</summary>
[Collection("database")]
public class PlaceProblemsTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };
    private static string Sample => Path.Combine(AppContext.BaseDirectory, "data", "overture-sample.jsonl");

    private static async Task Import(ApiFactory api)
    {
        using var scope = api.Services.CreateScope();
        await DirectoryImporter.ImportAsync(scope.ServiceProvider.GetRequiredService<RoadPulseDb>(), Sample,
            Microsoft.Extensions.Logging.Abstractions.NullLogger.Instance);
    }

    /// <summary>Corrections outlive imports by design, so each test removes its own (other tests share the database).</summary>
    private static async Task Reset(ApiFactory api)
    {
        await api.ClientAsync(); // makes sure the schema exists
        using var scope = api.Services.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<RoadPulseDb>();
        await db.PlaceProblems.ExecuteDeleteAsync();
        await db.PlaceCorrections.ExecuteDeleteAsync();
    }

    private static async Task<HttpClient> Device(ApiFactory api)
    {
        var client = await api.ClientAsync();
        var reg = (await (await client.PostAsync("/v1/devices", null)).Content.ReadFromJsonAsync<DeviceRegistration>(Json))!;
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", reg.Token);
        return client;
    }

    private static async Task<HttpClient> Moderator(ApiFactory api)
    {
        var mod = await api.ClientAsync();
        mod.DefaultRequestHeaders.Add(ModeratorAuthHandler.Header, ApiFactory.ModeratorKey);
        return mod;
    }

    private static async Task<List<DirectoryPlaceDto>> Search(HttpClient c, string q) =>
        (await c.GetFromJsonAsync<List<DirectoryPlaceDto>>($"/v1/places/search?q={q}&lat=3.16&lon=101.72", Json))!;

    [Fact]
    public async Task WrongPositionIsMovedAfterApprovalAndStaysMovedAfterANewImport()
    {
        using var api = new ApiFactory();
        await Reset(api);
        try { await WrongPositionIsMovedAfterApprovalAndStaysMovedAfterANewImportBody(api); }
        finally { await Reset(api); }
    }

    private static async Task WrongPositionIsMovedAfterApprovalAndStaysMovedAfterANewImportBody(ApiFactory api)
    {
        await Import(api);
        var me = await Device(api);
        var mall = (await Search(me, "mcity"))[0]; // "M-City Jalan Ampang" (id a1) at 3.1593, 101.7472

        // Needs a device, a known place, and something to go on.
        Assert.Equal(HttpStatusCode.Unauthorized, (await (await api.ClientAsync()).PostAsJsonAsync("/v1/places/problems", new { placeId = mall.Id, kind = "CLOSED" })).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, (await me.PostAsJsonAsync("/v1/places/problems", new { placeId = "nope", kind = "CLOSED" })).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await me.PostAsJsonAsync("/v1/places/problems", new { placeId = mall.Id, kind = "WRONG_POSITION" })).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await me.PostAsJsonAsync("/v1/places/problems", new { placeId = mall.Id, kind = "WRONG_POSITION", lat = 5.4, lon = 100.3 })).StatusCode);

        var sent = await me.PostAsJsonAsync("/v1/places/problems", new { placeId = mall.Id, kind = "WRONG_POSITION", lat = 3.1720, lon = 101.7150, note = "It is on Jalan Gurney Kiri" });
        Assert.Equal(HttpStatusCode.Created, sent.StatusCode);
        var problem = (await sent.Content.ReadFromJsonAsync<PlaceProblemDto>(Json))!;
        // Sending it again keeps one report.
        Assert.Equal(HttpStatusCode.OK, (await me.PostAsJsonAsync("/v1/places/problems", new { placeId = mall.Id, kind = "WRONG_POSITION", note = "Inside the training centre" })).StatusCode);

        // Nothing changes before a moderator looks.
        Assert.Equal(3.1593, (await Search(me, "mcity"))[0].Lat, 4);
        var mod = await Moderator(api);
        var item = Assert.Single((await mod.GetFromJsonAsync<List<PlaceProblemReviewItem>>("/v1/moderation/place-problems", Json))!);
        Assert.Equal(problem.Id, item.Problem.Id);
        Assert.Equal("Inside the training centre", item.Problem.Note);
        Assert.Equal(3.1593, item.CurrentLat!.Value, 4);
        Assert.InRange(item.MoveM!.Value, 3500, 4200);

        Assert.Equal(HttpStatusCode.BadRequest, (await mod.PostAsJsonAsync($"/v1/moderation/place-problems/{problem.Id}/approve", new { reason = "" })).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent, (await mod.PostAsJsonAsync($"/v1/moderation/place-problems/{problem.Id}/approve", new { reason = "Checked against the address" })).StatusCode);
        var moved = (await Search(me, "mcityjalan")).Single();
        Assert.Equal(3.1720, moved.Lat, 4);
        Assert.Equal(101.7150, moved.Lon, 4);
        Assert.Empty((await mod.GetFromJsonAsync<List<PlaceProblemReviewItem>>("/v1/moderation/place-problems", Json))!);

        // The monthly import brings the wrong position back; the correction is applied again.
        await Import(api);
        Assert.Equal(3.1720, (await Search(me, "mcityjalan")).Single().Lat, 4);

        // The driver's report is in their export and goes when they delete their data; the fix stays.
        Assert.Contains("Inside the training centre", await me.GetStringAsync("/v1/me/export"));
        Assert.Equal(HttpStatusCode.NoContent, (await me.DeleteAsync("/v1/me")).StatusCode);
        Assert.Equal(3.1720, (await Search(await Device(api), "mcityjalan")).Single().Lat, 4);
    }

    [Fact]
    public async Task ClosedPlaceIsHiddenAndAModeratorCanGiveThePositionOrReject()
    {
        using var api = new ApiFactory();
        await Reset(api);
        try { await ClosedPlaceIsHiddenAndAModeratorCanGiveThePositionOrRejectBody(api); }
        finally { await Reset(api); }
    }

    private static async Task ClosedPlaceIsHiddenAndAModeratorCanGiveThePositionOrRejectBody(ApiFactory api)
    {
        await Import(api);
        var me = await Device(api);
        var mod = await Moderator(api);
        var hits = await Search(me, "mcity");
        var clinic = hits.Single(h => h.Name == "M City Dental Clinic");
        var gym = hits.Single(h => h.Name.StartsWith("Sky Gym"));

        async Task<PlaceProblemDto> Report(object body) =>
            (await (await me.PostAsJsonAsync("/v1/places/problems", body)).Content.ReadFromJsonAsync<PlaceProblemDto>(Json))!;

        // Closed for good: hidden from search after approval, and still hidden after a new import.
        var closed = await Report(new { placeId = clinic.Id, kind = "CLOSED" });
        await mod.PostAsJsonAsync($"/v1/moderation/place-problems/{closed.Id}/approve", new { reason = "Shop front is empty" });
        Assert.DoesNotContain(await Search(me, "mcity"), h => h.Id == clinic.Id);
        await Import(api);
        Assert.DoesNotContain(await Search(me, "mcity"), h => h.Id == clinic.Id);

        // Wrong position with only a note: the moderator must type the position.
        var vague = await Report(new { placeId = gym.Id, kind = "WRONG_POSITION", note = "It is in the next block" });
        Assert.Equal(HttpStatusCode.BadRequest, (await mod.PostAsJsonAsync($"/v1/moderation/place-problems/{vague.Id}/approve", new { reason = "Checked" })).StatusCode);
        Assert.Equal(HttpStatusCode.NoContent, (await mod.PostAsJsonAsync($"/v1/moderation/place-problems/{vague.Id}/approve", new { reason = "Checked", lat = 3.1600, lon = 101.7480 })).StatusCode);
        Assert.Equal(3.1600, (await Search(me, "skygym")).Single().Lat, 4);

        // Rejected: nothing changes.
        var wrong = await Report(new { placeId = gym.Id, kind = "NOT_THERE" });
        Assert.Equal(HttpStatusCode.NoContent, (await mod.PostAsJsonAsync($"/v1/moderation/place-problems/{wrong.Id}/reject", new { reason = "The gym is there" })).StatusCode);
        Assert.Single(await Search(me, "skygym"));
        Assert.Equal(HttpStatusCode.Unauthorized, (await me.GetAsync("/v1/moderation/place-problems")).StatusCode);
    }
}
