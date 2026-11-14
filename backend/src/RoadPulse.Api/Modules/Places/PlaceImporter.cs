using System.Globalization;
using System.Text.Json;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using Microsoft.EntityFrameworkCore;
using RoadPulse.Api.Data;

namespace RoadPulse.Api.Modules.Places;

/// <summary>
/// Loads places from a GeoJSON text sequence made by osmium from the Malaysia map file
/// (see backend/setup-places.ps1). Replaces all places in one transaction. Areas (car parks,
/// rest areas) are stored as a point inside the area.
/// </summary>
public static class PlaceImporter
{
    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);

    /// <summary>Maps OpenStreetMap tags to a category, or null for places RoadPulse does not use.</summary>
    public static PlaceCategory? CategoryOf(IReadOnlyDictionary<string, string> tags) =>
        tags.GetValueOrDefault("barrier") == "toll_booth" ? PlaceCategory.TOLL_PLAZA :
        tags.GetValueOrDefault("hazard") == "school_zone" || tags.GetValueOrDefault("amenity") == "school" ? PlaceCategory.SCHOOL_ZONE :
        (tags.GetValueOrDefault("amenity"), tags.GetValueOrDefault("highway")) switch
        {
            ("fuel", _) => PlaceCategory.FUEL,
            ("parking", _) => PlaceCategory.PARKING,
            ("charging_station", _) => PlaceCategory.EV_CHARGING,
            ("restaurant" or "fast_food" or "cafe" or "food_court", _) => PlaceCategory.FOOD,
            (_, "rest_area" or "services") => PlaceCategory.REST_AREA,
            (_, "speed_camera") => PlaceCategory.SPEED_CAMERA,
            _ => null,
        };

    /// <summary>Parses one GeoJSON feature line; null when it is not a usable place.</summary>
    public static MapPlace? Parse(string line)
    {
        line = line.TrimStart('\u001e').Trim();
        if (line.Length == 0) return null;
        using var doc = JsonDocument.Parse(line);
        var root = doc.RootElement;
        if (!root.TryGetProperty("properties", out var props) || !root.TryGetProperty("geometry", out var geometry)) return null;

        var tags = new Dictionary<string, string>();
        foreach (var p in props.EnumerateObject())
            if (p.Value.ValueKind == JsonValueKind.String) tags[p.Name] = p.Value.GetString()!;
        if (CategoryOf(tags) is not { } category) return null;

        var type = tags.GetValueOrDefault("@type");
        var osmId = props.TryGetProperty("@id", out var idEl) ? idEl.ToString() : null;
        if (type is null || osmId is null) return null;

        var point = PointOf(ReadGeometry(geometry));
        if (point is null) return null;

        return new MapPlace
        {
            Id = type[0] + osmId,
            Category = category,
            Name = Trim(tags.GetValueOrDefault("name:en") ?? tags.GetValueOrDefault("name"), 200),
            Brand = Trim(tags.GetValueOrDefault("brand"), 100),
            Paid = category == PlaceCategory.PARKING ? tags.GetValueOrDefault("fee") switch { "yes" => true, "no" => false, _ => null } : null,
            MaxSpeedKph = category == PlaceCategory.SPEED_CAMERA ? ParseKph(tags.GetValueOrDefault("maxspeed")) : null,
            OpeningHours = category is PlaceCategory.FUEL or PlaceCategory.PARKING or PlaceCategory.FOOD or PlaceCategory.EV_CHARGING or PlaceCategory.REST_AREA
                ? Trim(tags.GetValueOrDefault("opening_hours"), 255) : null,
            Location = point,
        };
    }

    /// <summary>Imports every place in [path]; returns how many were stored.</summary>
    public static async Task<int> ImportAsync(RoadPulseDb db, string path, ILogger log, CancellationToken ct = default)
    {
        var places = new Dictionary<string, MapPlace>();
        foreach (var line in File.ReadLines(path))
        {
            var place = Parse(line);
            if (place is not null) places[place.Id] = place; // the same object can appear twice
        }
        await using var tx = await db.Database.BeginTransactionAsync(ct);
        await db.Places.ExecuteDeleteAsync(ct);
        foreach (var batch in places.Values.Chunk(5000))
        {
            db.Places.AddRange(batch);
            await db.SaveChangesAsync(ct);
            db.ChangeTracker.Clear();
        }
        await tx.CommitAsync(ct);
        log.LogInformation("Imported {Count} places", places.Count);
        return places.Count;
    }

    /// <summary>The geometry kinds osmium export writes: Point, LineString, Polygon, MultiPolygon.</summary>
    private static Geometry? ReadGeometry(JsonElement g)
    {
        if (!g.TryGetProperty("type", out var typeEl) || !g.TryGetProperty("coordinates", out var c)) return null;
        Coordinate Coord(JsonElement a) => new(a[0].GetDouble(), a[1].GetDouble());
        Coordinate[] Ring(JsonElement r) => r.EnumerateArray().Select(Coord).ToArray();
        Polygon Poly(JsonElement p)
        {
            var rings = p.EnumerateArray().Select(Ring).ToArray();
            return Geo.CreatePolygon(Geo.CreateLinearRing(rings[0]), rings.Skip(1).Select(r => Geo.CreateLinearRing(r)).ToArray());
        }
        try
        {
            return typeEl.GetString() switch
            {
                "Point" => Geo.CreatePoint(Coord(c)),
                "LineString" => Geo.CreateLineString(Ring(c)),
                "Polygon" => Poly(c),
                "MultiPolygon" => Geo.CreateMultiPolygon(c.EnumerateArray().Select(Poly).ToArray()),
                _ => null,
            };
        }
        catch (ArgumentException)
        {
            return null; // broken ring in the source data: skip this place
        }
    }

    private static Point? PointOf(Geometry? g)
    {
        if (g is null || g.IsEmpty) return null;
        // A point inside the area, so a car park's marker is never outside it.
        var p = g is Point pt ? pt : g.InteriorPoint;
        return Geo.CreatePoint(new Coordinate(p.X, p.Y));
    }

    private static string? Trim(string? s, int max) => string.IsNullOrWhiteSpace(s) ? null : (s.Length > max ? s[..max] : s.Trim());

    private static int? ParseKph(string? maxspeed)
    {
        if (string.IsNullOrWhiteSpace(maxspeed)) return null;
        var digits = new string(maxspeed.TakeWhile(char.IsDigit).ToArray());
        if (!int.TryParse(digits, NumberStyles.Integer, CultureInfo.InvariantCulture, out var v)) return null;
        return maxspeed.Contains("mph") ? (int)Math.Round(v * 1.609) : v;
    }
}
