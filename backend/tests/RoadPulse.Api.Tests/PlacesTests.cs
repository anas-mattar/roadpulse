using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Places;

namespace RoadPulse.Api.Tests;

public class PlaceImporterTests
{
    [Fact]
    public void ParsesNodesAreasAndCameras()
    {
        var fuel = PlaceImporter.Parse("""{"type":"Feature","geometry":{"type":"Point","coordinates":[101.69,3.14]},"properties":{"@type":"node","@id":1,"amenity":"fuel","name":"Petronas Jalan Ampang","brand":"Petronas"}}""")!;
        Assert.Equal("n1", fuel.Id);
        Assert.Equal(PlaceCategory.FUEL, fuel.Category);
        Assert.Equal("Petronas", fuel.Brand);

        // A car park mapped as an area becomes one point inside it.
        var park = PlaceImporter.Parse("\u001e" + """{"type":"Feature","geometry":{"type":"MultiPolygon","coordinates":[[[[101.70,3.10],[101.71,3.10],[101.71,3.11],[101.70,3.11],[101.70,3.10]]]]},"properties":{"@type":"way","@id":2,"amenity":"parking","fee":"yes"}}""")!;
        Assert.Equal("w2", park.Id);
        Assert.True(park.Paid);
        Assert.InRange(park.Location.X, 101.70, 101.71);
        Assert.InRange(park.Location.Y, 3.10, 3.11);

        var cam = PlaceImporter.Parse("""{"type":"Feature","geometry":{"type":"Point","coordinates":[101.6,3.0]},"properties":{"@type":"node","@id":3,"highway":"speed_camera","maxspeed":"90"}}""")!;
        Assert.Equal(PlaceCategory.SPEED_CAMERA, cam.Category);
        Assert.Equal(90, cam.MaxSpeedKph);

        var rnr = PlaceImporter.Parse("""{"type":"Feature","geometry":{"type":"Point","coordinates":[101.6,3.0]},"properties":{"@type":"node","@id":4,"highway":"services","name":"R&R Dengkil"}}""")!;
        Assert.Equal(PlaceCategory.REST_AREA, rnr.Category);

        Assert.Null(PlaceImporter.Parse("""{"type":"Feature","geometry":{"type":"Point","coordinates":[1,1]},"properties":{"@type":"node","@id":5,"shop":"bakery"}}"""));

        var school = PlaceImporter.Parse("""{"type":"Feature","geometry":{"type":"Point","coordinates":[101.6,3.0]},"properties":{"@type":"way","@id":6,"amenity":"school","name":"SK Bangsar"}}""")!;
        Assert.Equal(PlaceCategory.SCHOOL_ZONE, school.Category);
        var zone = PlaceImporter.Parse("""{"type":"Feature","geometry":{"type":"Point","coordinates":[101.6,3.0]},"properties":{"@type":"node","@id":7,"hazard":"school_zone"}}""")!;
        Assert.Equal(PlaceCategory.SCHOOL_ZONE, zone.Category);
        var shell = PlaceImporter.Parse("""{"type":"Feature","geometry":{"type":"Point","coordinates":[101.6,3.0]},"properties":{"@type":"node","@id":8,"amenity":"fuel","opening_hours":"24/7"}}""")!;
        Assert.Equal("24/7", shell.OpeningHours);
        Assert.Null(school.OpeningHours);
        Assert.Null(PlaceImporter.Parse(""));
    }
}

[Collection("database")]
public class PlacesApiTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };

    private static string Feature(string type, long id, double lon, double lat, string tags) =>
        "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[" +
        FormattableString.Invariant($"{lon},{lat}") + "]},\"properties\":{\"@type\":\"" + type + "\",\"@id\":" + id + "," + tags + "}}";

    private static async Task<HttpClient> WithPlaces(ApiFactory api)
    {
        var client = await api.ClientAsync();
        var file = Path.GetTempFileName();
        await File.WriteAllLinesAsync(file, [
            Feature("node", 10, 101.700, 3.150, "\"amenity\":\"fuel\",\"name\":\"Near\""),
            Feature("node", 11, 101.720, 3.150, "\"amenity\":\"fuel\",\"name\":\"Far\""),
            Feature("node", 12, 101.701, 3.151, "\"amenity\":\"parking\",\"fee\":\"no\""),
            Feature("node", 13, 101.650, 3.100, "\"highway\":\"speed_camera\",\"maxspeed\":\"80\""),  // on the test route
            Feature("node", 14, 101.650, 3.110, "\"highway\":\"speed_camera\""),                       // 1.1 km off it
        ]);
        using var scope = api.Services.CreateScope();
        var count = await PlaceImporter.ImportAsync(scope.ServiceProvider.GetRequiredService<RoadPulseDb>(), file, NullLogger.Instance);
        Assert.Equal(5, count);
        return client;
    }

    [Fact]
    public async Task NearbyIsSortedByDistanceAndFilteredByCategory()
    {
        using var api = new ApiFactory();
        var c = await WithPlaces(api);
        var fuel = (await c.GetFromJsonAsync<List<PlaceDto>>("/v1/places/nearby?category=FUEL&lat=3.150&lon=101.699&radiusM=5000", Json))!;
        Assert.Equal(["Near", "Far"], fuel.Select(p => p.Name));
        Assert.InRange(fuel[0].DistanceM, 100, 120);
        var close = (await c.GetFromJsonAsync<List<PlaceDto>>("/v1/places/nearby?category=FUEL&lat=3.150&lon=101.699&radiusM=500", Json))!;
        Assert.Single(close);
        var parking = Assert.Single((await c.GetFromJsonAsync<List<PlaceDto>>("/v1/places/nearby?category=PARKING&lat=3.150&lon=101.699", Json))!);
        Assert.False(parking.Paid);
    }

    [Fact]
    public async Task CamerasAlongARouteOnly()
    {
        using var api = new ApiFactory();
        var c = await WithPlaces(api);
        var res = await c.PostAsJsonAsync("/v1/places/along-route", new
        {
            category = "SPEED_CAMERA",
            points = new[] { new[] { 3.100, 101.640 }, new[] { 3.100, 101.660 } },
        });
        var cams = (await res.Content.ReadFromJsonAsync<List<PlaceDto>>(Json))!;
        Assert.Equal(80, Assert.Single(cams).MaxSpeedKph);
    }

    [Fact]
    public async Task BadRequestsAreRefused()
    {
        using var api = new ApiFactory();
        var c = await api.ClientAsync();
        Assert.Equal(HttpStatusCode.BadRequest, (await c.GetAsync("/v1/places/nearby?category=FUEL&lat=3&lon=101&radiusM=999999")).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await c.GetAsync("/v1/places/nearby?category=FUEL&lon=101")).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await c.GetAsync("/v1/places/nearby?category=UFO&lat=3&lon=101")).StatusCode);
        var onePoint = await c.PostAsJsonAsync("/v1/places/along-route", new { category = "SPEED_CAMERA", points = new[] { new[] { 3.1, 101.6 } } });
        Assert.Equal(HttpStatusCode.BadRequest, onePoint.StatusCode);
    }
}
