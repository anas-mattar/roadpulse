using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Places;

namespace RoadPulse.Api.Modules.Tolls;

public record TollEstimateRequest(List<double[]> Points, TollVehicle? Vehicle, double? CorridorM);

/// <summary>Why a plaza on the route has (or has no) price.</summary>
public enum TollPlazaStatus
{
    /// <summary>In the table with a price for this vehicle.</summary>
    PRICED,
    /// <summary>In the table, but the price for this vehicle is empty.</summary>
    NO_PRICE,
    /// <summary>Closed (distance-based) system such as PLUS: not priced yet.</summary>
    CLOSED_SYSTEM,
    /// <summary>The map has a plaza here, but its name is not in the table (or it has no name).</summary>
    NOT_IN_TABLE,
}

public record TollPlazaDto(string? Name, string? Highway, TollPlazaStatus Status, decimal? PriceRm, double Lat, double Lon);

/// <param name="TotalRm">Sum of the priced plazas only.</param>
/// <param name="Complete">True when every plaza on the route has a price, so TotalRm is the whole toll.</param>
public record TollEstimateDto(string Currency, TollVehicle Vehicle, decimal TotalRm, bool Complete,
    List<TollPlazaDto> Plazas, string? RatesCheckedOn);

/// <summary>
/// Estimated toll for a route: the toll plazas (from OpenStreetMap) the route passes through, priced
/// from the owner's table (tolls/toll-rates.csv). An estimate: the table may be out of date.
/// </summary>
public static class TollEndpoints
{
    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);

    /// <summary>Two map points of one plaza (e.g. both carriageways, or one per lane group) closer than this are one plaza.</summary>
    public const double SamePlazaM = 400;

    public static RouteGroupBuilder MapTollEndpoints(this RouteGroupBuilder v1)
    {
        v1.MapPost("/tolls/estimate", Estimate).WithTags("Tolls").WithName("TollEstimate")
            .RequireRateLimiting(RateLimits.Reads)
            .WithSummary("Estimated toll for a route given as [lat, lon] points; vehicle CAR (Class 1, default) or MOTORCYCLE (Class 0).");
        return v1;
    }

    private static async Task<Results<Ok<TollEstimateDto>, ValidationProblem>> Estimate(
        TollEstimateRequest request, RoadPulseDb db, TollRateStore store)
    {
        var errors = new Dictionary<string, string[]>();
        if (request.Points is not { Count: >= 2 } || request.Points.Count > PlaceEndpoints.MaxRoutePoints)
            errors["points"] = [$"Between 2 and {PlaceEndpoints.MaxRoutePoints} [lat, lon] points."];
        else if (request.Points.Any(p => p.Length != 2 || p[0] is < -90 or > 90 || p[1] is < -180 or > 180))
            errors["points"] = ["Each point is [lat, lon] within world bounds."];
        var corridor = request.CorridorM ?? 30;
        if (corridor is <= 0 or > 100) errors["corridorM"] = ["Must be between 1 and 100."];
        var vehicle = request.Vehicle ?? TollVehicle.CAR;
        if (!Enum.IsDefined(vehicle)) errors["vehicle"] = ["CAR or MOTORCYCLE."];
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);

        var coords = request.Points.Select(p => new Coordinate(p[1], p[0])).ToArray();
        var line = Geo.CreateLineString(coords);
        var found = await db.Places.AsNoTracking()
            .Where(p => p.Category == PlaceCategory.TOLL_PLAZA && p.Location.IsWithinDistance(line, corridor))
            .Take(500)
            .ToListAsync();

        var rates = store.Current();
        var plazas = Merge(found, rates, line);
        var dtos = Price(plazas, store.CurrentPairs(), vehicle);
        var total = dtos.Where(d => d.Status == TollPlazaStatus.PRICED).Sum(d => d.PriceRm ?? 0);
        var checkedOn = plazas.Select(p => p.Rate?.CheckedOn).Where(d => d is not null).Order().FirstOrDefault();
        // (Closed-system pairs carry their own date; the oldest open-plaza date is the useful warning.)
        return TypedResults.Ok(new TollEstimateDto("MYR", vehicle, total,
            dtos.All(d => d.Status == TollPlazaStatus.PRICED), dtos, checkedOn));
    }

    /// <summary>
    /// One entry per real plaza, in driving order. Map points of the same plaza (same table row, or
    /// same name, or unnamed and close together) count once.
    /// </summary>
    public static List<(MapPlace Place, TollRate? Rate)> Merge(List<MapPlace> found, TollRates rates, LineString route)
    {
        var along = new NetTopologySuite.LinearReferencing.LengthIndexedLine(route);
        var ordered = found
            .Select(p => (Place: p, Rate: rates.Find(p.Name), Along: along.Project(p.Location.Coordinate)))
            .OrderBy(x => x.Along)
            .ToList();
        var kept = new List<(MapPlace Place, TollRate? Rate)>();
        foreach (var x in ordered)
        {
            var same = kept.Any(k =>
                (x.Rate is not null && ReferenceEquals(k.Rate, x.Rate)) ||
                (x.Rate is null && k.Rate is null && x.Place.Name is not null &&
                 TollRates.Normalise(k.Place.Name) == TollRates.Normalise(x.Place.Name)) ||
                (x.Place.Name is null && Metres(k.Place.Location.Coordinate, x.Place.Location.Coordinate) < SamePlazaM));
            if (!same) kept.Add((x.Place, x.Rate));
        }
        return kept;
    }

    /// <summary>
    /// Prices each plaza. On a closed-system highway (PLUS etc.) the first and last plaza passed are the
    /// entry and exit: when that pair is in toll-pairs.csv they become one priced trip, "Entry - Exit".
    /// </summary>
    public static List<TollPlazaDto> Price(List<(MapPlace Place, TollRate? Rate)> plazas, TollPairs pairs, TollVehicle vehicle)
    {
        var result = new List<TollPlazaDto>();
        for (var i = 0; i < plazas.Count; i++)
        {
            var (place, rate) = plazas[i];
            if (rate is { System: TollSystem.CLOSED })
            {
                var end = i;
                while (end + 1 < plazas.Count && plazas[end + 1].Rate is { System: TollSystem.CLOSED } next &&
                       string.Equals(next.Highway, rate.Highway, StringComparison.OrdinalIgnoreCase)) end++;
                var exit = plazas[end].Rate!;
                if (end > i && pairs.Find(rate.Highway, rate.Plaza, exit.Plaza) is { } pair)
                {
                    var price = pair.PriceFor(vehicle);
                    result.Add(new TollPlazaDto($"{rate.Plaza} - {exit.Plaza}", rate.Highway,
                        price is null ? TollPlazaStatus.NO_PRICE : TollPlazaStatus.PRICED, price, place.Location.Y, place.Location.X));
                    i = end;
                    continue;
                }
            }
            result.Add(ToDto(place, rate, vehicle));
        }
        return result;
    }

    private static TollPlazaDto ToDto(MapPlace p, TollRate? rate, TollVehicle vehicle)
    {
        var (status, price) = rate switch
        {
            null => (TollPlazaStatus.NOT_IN_TABLE, (decimal?)null),
            { System: TollSystem.CLOSED } => (TollPlazaStatus.CLOSED_SYSTEM, null),
            _ when rate.PriceFor(vehicle) is { } v => (TollPlazaStatus.PRICED, v),
            _ => (TollPlazaStatus.NO_PRICE, null),
        };
        return new TollPlazaDto(rate?.Plaza ?? p.Name, rate?.Highway, status, price, p.Location.Y, p.Location.X);
    }

    private static double Metres(Coordinate a, Coordinate b)
    {
        var kx = 111_320 * Math.Cos(a.Y * Math.PI / 180);
        return Math.Sqrt(Math.Pow((a.X - b.X) * kx, 2) + Math.Pow((a.Y - b.Y) * 110_540, 2));
    }
}
