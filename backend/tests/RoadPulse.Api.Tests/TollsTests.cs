using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Places;
using RoadPulse.Api.Modules.Tolls;

namespace RoadPulse.Api.Tests;

public class TollRatesTests
{
    private const string Csv = """
        # comment line
        plaza,aliases,highway,system,car_rm,motorcycle_rm,source,checked_on,notes
        Bukit Kiara,,SPRINT,OPEN,3.00,,litrak,2026-11-15,
        "Batu 9","Batu Sembilan",Cheras-Kajang,OPEN,1.30,0,src,2026-11-01,
        Batu,,DUKE,OPEN,2.50,,src,,
        Sungai Long,Sg Long,SILK,OPEN,RM1.66,,src,,
        Ampang,,SUKE,OPEN,2.30,,src,,
        Ampang,,AKLEH,OPEN,1.00,,src,,
        Jalan Duta,,PLUS,CLOSED,,,src,,
        Broken,,X,OPEN,abc,,src,,
        "# Excel-style comment",,,,,,,,
        ,,,,,,,,
        """;

    [Fact]
    public void ReadsTheTableAndReportsBadRows()
    {
        var t = TollRates.Parse("﻿" + Csv);
        Assert.Equal(7, t.All.Count);
        Assert.Contains(t.Problems, p => p.Contains("Broken"));
        Assert.Equal(1.66m, t.Find("Sungai Long")!.CarRm);
        Assert.Equal(TollSystem.CLOSED, t.Find("Jalan Duta")!.System);
    }

    [Theory]
    [InlineData("Plaza Tol Bukit Kiara", "Bukit Kiara")]
    [InlineData("Bukit Kiara Toll Plaza", "Bukit Kiara")]
    [InlineData("PLAZA TOL BUKIT KIARA (ARAH BARAT)", "Bukit Kiara")]
    [InlineData("Plaza Tol Batu 9 Arah Kajang", "Batu 9")]
    [InlineData("Plaza Tol Batu Sembilan", "Batu 9")]
    [InlineData("Plaza Tol Batu", "Batu")]
    [InlineData("Plaza Tol Sg. Long", "Sungai Long")]
    [InlineData("Bukit Kiara Northbound", "Bukit Kiara")]
    public void MatchesMapNames(string osmName, string plaza) =>
        Assert.Equal(plaza, TollRates.Parse(Csv).Find(osmName)?.Plaza);

    [Theory]
    [InlineData("Plaza Tol Ampang")]   // two highways: ambiguous, so no price rather than a wrong one
    [InlineData("Plaza Tol Batu Tiga")] // not "Batu"
    [InlineData(null)]
    [InlineData("Plaza Tol")]
    public void UnknownOrAmbiguousNamesHaveNoRate(string? osmName) =>
        Assert.Null(TollRates.Parse(Csv).Find(osmName));

    [Fact]
    public void AnEmptyOrHeaderlessFileGivesNoRates()
    {
        Assert.Empty(TollRates.Parse("").All);
        Assert.NotEmpty(TollRates.Parse("a,b,c\n1,2,3").Problems);
    }

    [Fact]
    public void ThePrefilledTableIsValid()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "../../../../../tolls/toll-rates.csv");
        var t = TollRates.Parse(File.ReadAllText(path));
        Assert.Empty(t.Problems);
        Assert.True(t.All.Count >= 40);
        Assert.All(t.All, r => Assert.NotNull(r.CarRm));
        Assert.Equal(3.00m, t.Find("Plaza Tol Bukit Kiara")!.CarRm);
    }
}

[Collection("database")]
public class TollEstimateApiTests
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { Converters = { new JsonStringEnumConverter() } };

    private static string Booth(long id, double lon, double lat, string? name) =>
        "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[" + FormattableString.Invariant($"{lon},{lat}") +
        "]},\"properties\":{\"@type\":\"node\",\"@id\":" + id + ",\"barrier\":\"toll_booth\"" +
        (name is null ? "" : ",\"name\":\"" + name + "\"") + "}}";

    private static async Task<(ApiFactory Api, HttpClient Client)> Setup()
    {
        var rates = Path.GetTempFileName();
        await File.WriteAllTextAsync(rates, """
            plaza,aliases,highway,system,car_rm,motorcycle_rm,source,checked_on,notes
            Bukit Kiara,,SPRINT,OPEN,3.00,,litrak,2026-11-15,
            Damansara,,SPRINT,OPEN,2.00,0.00,litrak,2026-11-10,
            Jalan Duta,,PLUS,CLOSED,,,src,,
            """);
        var api = new ApiFactory { TollRatesPath = rates };
        var client = await api.ClientAsync();
        var places = Path.GetTempFileName();
        // Route along lat 3.100 from lon 101.600 to 101.700.
        await File.WriteAllLinesAsync(places, [
            Booth(1, 101.6100, 3.1000, "Plaza Tol Bukit Kiara (Arah Barat)"),
            Booth(2, 101.6101, 3.1001, "Plaza Tol Bukit Kiara (Arah Timur)"), // other carriageway: same plaza
            Booth(3, 101.6500, 3.1000, "Plaza Tol Damansara"),
            Booth(4, 101.6700, 3.1000, "Plaza Tol Jalan Duta"),
            Booth(5, 101.6900, 3.1000, null),
            Booth(6, 101.6901, 3.1000, null),                                // same unnamed plaza
            Booth(7, 101.6300, 3.1200, "Plaza Tol Far Away"),                // 2 km off the route
        ]);
        using var scope = api.Services.CreateScope();
        await PlaceImporter.ImportAsync(scope.ServiceProvider.GetRequiredService<RoadPulseDb>(), places, NullLogger.Instance);
        return (api, client);
    }

    private static object Route(string? vehicle = null) => new
    {
        points = new[] { new[] { 3.100, 101.700 }, new[] { 3.100, 101.600 } }, // driving west
        vehicle,
    };

    [Fact]
    public async Task SumsPricedPlazasInDrivingOrderAndFlagsTheRest()
    {
        var (api, c) = await Setup();
        using var _ = api;
        var res = await c.PostAsJsonAsync("/v1/tolls/estimate", Route());
        res.EnsureSuccessStatusCode();
        var e = (await res.Content.ReadFromJsonAsync<TollEstimateDto>(Json))!;
        Assert.Equal(5.00m, e.TotalRm);
        Assert.False(e.Complete);
        Assert.Equal("MYR", e.Currency);
        Assert.Equal(
            [TollPlazaStatus.NOT_IN_TABLE, TollPlazaStatus.CLOSED_SYSTEM, TollPlazaStatus.PRICED, TollPlazaStatus.PRICED],
            e.Plazas.Select(p => p.Status));
        Assert.Equal(["Jalan Duta", "Damansara", "Bukit Kiara"], e.Plazas.Skip(1).Select(p => p.Name));
        Assert.Equal("2026-11-10", e.RatesCheckedOn);
    }

    [Fact]
    public async Task MotorcyclePricesAreSeparate()
    {
        var (api, c) = await Setup();
        using var _ = api;
        var e = (await (await c.PostAsJsonAsync("/v1/tolls/estimate", Route("MOTORCYCLE"))).Content.ReadFromJsonAsync<TollEstimateDto>(Json))!;
        Assert.Equal(0m, e.TotalRm);
        Assert.Equal(TollPlazaStatus.NO_PRICE, e.Plazas.Single(p => p.Name == "Bukit Kiara").Status);
        Assert.Equal(TollPlazaStatus.PRICED, e.Plazas.Single(p => p.Name == "Damansara").Status);
    }

    [Fact]
    public async Task ARouteWithoutPlazasIsCompleteAndFree()
    {
        var (api, c) = await Setup();
        using var _ = api;
        var e = (await (await c.PostAsJsonAsync("/v1/tolls/estimate", new { points = new[] { new[] { 3.2, 101.6 }, new[] { 3.2, 101.7 } } }))
            .Content.ReadFromJsonAsync<TollEstimateDto>(Json))!;
        Assert.Empty(e.Plazas);
        Assert.True(e.Complete);
        Assert.Equal(0m, e.TotalRm);
    }

    [Fact]
    public async Task BadRequestsAreRefused()
    {
        using var api = new ApiFactory();
        var c = await api.ClientAsync();
        Assert.Equal(HttpStatusCode.BadRequest, (await c.PostAsJsonAsync("/v1/tolls/estimate", new { points = new[] { new[] { 3.1, 101.6 } } })).StatusCode);
        Assert.Equal(HttpStatusCode.BadRequest, (await c.PostAsJsonAsync("/v1/tolls/estimate", new { points = new[] { new[] { 3.1, 101.6 }, new[] { 3.2, 101.6 } }, vehicle = "TRUCK" })).StatusCode);
    }

    [Fact]
    public void ImporterKnowsTollBooths() =>
        Assert.Equal(PlaceCategory.TOLL_PLAZA, PlaceImporter.Parse(Booth(9, 101.6, 3.1, "Plaza Tol X"))!.Category);
}

public class TollPairsTests
{
    private static readonly TollRates Rates = TollRates.Parse("""
        plaza,aliases,highway,system,car_rm,motorcycle_rm,source,checked_on,notes
        Jalan Duta,,PLUS,CLOSED,,,s,,
        Rawang,,PLUS,CLOSED,,,s,,
        Sungai Buloh,,PLUS,CLOSED,,,s,,
        Bukit Kiara,,SPRINT,OPEN,3.00,,s,,
        """);

    private static readonly TollPairs Pairs = TollPairs.Parse("""
        highway,entry,exit,car_rm,motorcycle_rm,source,checked_on,notes
        PLUS,Jalan Duta,Rawang,4.50,,test,,
        PLUS,Jalan Duta,Bad,x,,test,,
        """);

    private static (MapPlace, TollRate?) P(string name)
    {
        var place = new MapPlace { Id = name, Category = PlaceCategory.TOLL_PLAZA, Name = name,
            Location = new NetTopologySuite.Geometries.Point(101.6, 3.1) { SRID = 4326 } };
        return (place, Rates.Find(name));
    }

    [Fact]
    public void ReadsPairsBothWays()
    {
        Assert.Single(Pairs.All);
        Assert.Single(Pairs.Problems);
        Assert.Equal(4.50m, Pairs.Find("PLUS", "Rawang", "Jalan Duta")!.CarRm);
        Assert.Null(Pairs.Find("PLUS", "Rawang", "Sungai Buloh"));
    }

    [Fact]
    public void AClosedSystemTripIsOnePricedEntry()
    {
        var dtos = TollEndpoints.Price([P("Plaza Tol Rawang"), P("Plaza Tol Jalan Duta"), P("Plaza Tol Bukit Kiara")], Pairs, TollVehicle.CAR);
        Assert.Equal(["Rawang - Jalan Duta", "Bukit Kiara"], dtos.Select(d => d.Name));
        Assert.All(dtos, d => Assert.Equal(TollPlazaStatus.PRICED, d.Status));
        Assert.Equal(7.50m, dtos.Sum(d => d.PriceRm));
    }

    [Fact]
    public void UnknownTripsStayUnpriced()
    {
        var dtos = TollEndpoints.Price([P("Rawang"), P("Sungai Buloh")], Pairs, TollVehicle.CAR);
        Assert.All(dtos, d => Assert.Equal(TollPlazaStatus.CLOSED_SYSTEM, d.Status));
        var moto = TollEndpoints.Price([P("Rawang"), P("Jalan Duta")], Pairs, TollVehicle.MOTORCYCLE);
        Assert.Equal(TollPlazaStatus.NO_PRICE, Assert.Single(moto).Status);
    }

    [Fact]
    public void ThePairsTemplateIsValid()
    {
        var path = Path.Combine(AppContext.BaseDirectory, "../../../../../tolls/toll-pairs.csv");
        Assert.Empty(TollPairs.Parse(File.ReadAllText(path)).Problems);
    }
}
