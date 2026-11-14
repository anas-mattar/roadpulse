using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using RoadPulse.Api.Data;

namespace RoadPulse.Api.Modules.Places;

public record PlaceDto(string Id, PlaceCategory Category, string? Name, string? Brand, double Lat, double Lon,
    bool? Paid, int? MaxSpeedKph, double DistanceM);
public record AlongRouteRequest(PlaceCategory Category, List<double[]> Points, double? CorridorM);

/// <summary>
/// Places from OpenStreetMap: nearby search by category (fuel, parking, food, EV charging, rest
/// areas) and places along a route (speed cameras). Public, like the report list.
/// </summary>
public static class PlaceEndpoints
{
    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);
    public const double MaxRadiusM = 50_000;
    public const int MaxRoutePoints = 5_000;

    public static RouteGroupBuilder MapPlaceEndpoints(this RouteGroupBuilder v1)
    {
        var places = v1.MapGroup("/places").WithTags("Places").RequireRateLimiting(RateLimits.Reads);
        places.MapGet("/nearby", Nearby).WithName("PlacesNearby")
            .WithSummary("Places of one category nearest to a point: category, lat, lon, radiusM (max 50 km), limit (max 50).");
        places.MapPost("/along-route", AlongRoute).WithName("PlacesAlongRoute")
            .WithSummary("Places of one category within corridorM (default 30, max 100) of a route given as [lat, lon] points.");
        return v1;
    }

    private static async Task<Results<Ok<List<PlaceDto>>, ValidationProblem>> Nearby(
        PlaceCategory? category, double? lat, double? lon, double? radiusM, int? limit, RoadPulseDb db)
    {
        var errors = new Dictionary<string, string[]>();
        if (category is null || !Enum.IsDefined(category.Value)) errors["category"] = [$"One of {string.Join(", ", Enum.GetNames<PlaceCategory>())}."];
        if (lat is not (>= -90 and <= 90)) errors["lat"] = ["Must be between -90 and 90."];
        if (lon is not (>= -180 and <= 180)) errors["lon"] = ["Must be between -180 and 180."];
        var radius = radiusM ?? 5_000;
        if (radius is <= 0 or > MaxRadiusM) errors["radiusM"] = [$"Must be between 1 and {MaxRadiusM}."];
        var take = limit ?? 20;
        if (take is < 1 or > 50) errors["limit"] = ["Must be between 1 and 50."];
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);

        var here = Geo.CreatePoint(new Coordinate(lon!.Value, lat!.Value));
        var rows = await db.Places.AsNoTracking()
            .Where(p => p.Category == category && p.Location.IsWithinDistance(here, radius))
            .Select(p => new { Place = p, Distance = p.Location.Distance(here) })
            .OrderBy(x => x.Distance)
            .Take(take)
            .ToListAsync();
        return TypedResults.Ok(rows.Select(x => ToDto(x.Place, x.Distance)).ToList());
    }

    private static async Task<Results<Ok<List<PlaceDto>>, ValidationProblem>> AlongRoute(AlongRouteRequest request, RoadPulseDb db)
    {
        var errors = new Dictionary<string, string[]>();
        if (!Enum.IsDefined(request.Category)) errors["category"] = ["Unknown category."];
        if (request.Points is not { Count: >= 2 } || request.Points.Count > MaxRoutePoints)
            errors["points"] = [$"Between 2 and {MaxRoutePoints} [lat, lon] points."];
        else if (request.Points.Any(p => p.Length != 2 || p[0] is < -90 or > 90 || p[1] is < -180 or > 180))
            errors["points"] = ["Each point is [lat, lon] within world bounds."];
        var corridor = request.CorridorM ?? 30;
        if (corridor is <= 0 or > 100) errors["corridorM"] = ["Must be between 1 and 100."];
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);

        var line = Geo.CreateLineString(request.Points.Select(p => new Coordinate(p[1], p[0])).ToArray());
        var rows = await db.Places.AsNoTracking()
            .Where(p => p.Category == request.Category && p.Location.IsWithinDistance(line, corridor))
            .Take(500)
            .ToListAsync();
        return TypedResults.Ok(rows.Select(p => ToDto(p, 0)).ToList());
    }

    private static PlaceDto ToDto(MapPlace p, double distance) =>
        new(p.Id, p.Category, p.Name, p.Brand, p.Location.Y, p.Location.X, p.Paid, p.MaxSpeedKph, Math.Round(distance));
}
