using System.Text.Json;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using NetTopologySuite;
using NetTopologySuite.Geometries;
using Npgsql;
using NpgsqlTypes;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.CommunityPlaces;

namespace RoadPulse.Api.Modules.Directory;

public record DirectoryPlaceDto(string Id, string Name, string? Category, string? Address, double Lat, double Lon, double DistanceM);

/// <summary>
/// Search over Overture Maps places (open data with far more shops, clinics, offices and condos in
/// Malaysia than OpenStreetMap). Loaded by setup-overture-places.ps1; searched by the apps next to
/// OpenStreetMap and places drivers added.
/// </summary>
public static class DirectoryEndpoints
{
    private static readonly GeometryFactory Geo = NtsGeometryServices.Instance.CreateGeometryFactory(srid: 4326);
    public const double SearchRadiusM = 100_000;
    public const float LowConfidence = 0.5f;

    public static RouteGroupBuilder MapDirectoryEndpoints(this RouteGroupBuilder v1)
    {
        v1.MapGroup("/places").WithTags("Places").RequireRateLimiting(RateLimits.Reads)
            .MapGet("/search", Search).WithName("SearchPlaces")
            .WithSummary("Named places (Overture Maps) whose name contains q, within 100 km of lat/lon: names starting with q first, then nearest.");
        return v1;
    }

    private static async Task<Results<Ok<List<DirectoryPlaceDto>>, ValidationProblem>> Search(
        string? q, double? lat, double? lon, int? limit, RoadPulseDb db)
    {
        var errors = new Dictionary<string, string[]>();
        var key = CommunityPlaceEndpoints.Key(q ?? "");
        if (key.Length is < 2 or > 80) errors["q"] = ["2 to 80 letters or digits."];
        if (lat is not (>= -90 and <= 90) || lon is not (>= -180 and <= 180)) errors["position"] = ["lat and lon are required."];
        var take = limit ?? 10;
        if (take is < 1 or > 30) errors["limit"] = ["Must be between 1 and 30."];
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);

        var here = Geo.CreatePoint(new Coordinate(lon!.Value, lat!.Value));
        // Names starting with the text first; then nearest, where a place Overture is unsure about
        // (confidence under 0.5) counts as three times as far away.
        var rows = await db.DirectoryPlaces.AsNoTracking()
            .Where(p => p.SearchKey.Contains(key) && p.Location.IsWithinDistance(here, SearchRadiusM))
            .Select(p => new { Place = p, Starts = p.SearchKey.StartsWith(key), Distance = p.Location.Distance(here) })
            .OrderByDescending(x => x.Starts)
            .ThenBy(x => x.Place.Confidence < LowConfidence ? x.Distance * 3 : x.Distance)
            .Take(take)
            .ToListAsync();
        return TypedResults.Ok(rows.Select(x => new DirectoryPlaceDto(
            x.Place.Id, x.Place.Name, x.Place.Category, x.Place.Address, x.Place.Location.Y, x.Place.Location.X, Math.Round(x.Distance))).ToList());
    }
}

/// <summary>
/// Loads the file made by tools/overture/export.py (one JSON object per line) into
/// directory_places, replacing what was there, in one transaction (search keeps working with the
/// old data until the new data is complete).
/// </summary>
public static class DirectoryImporter
{
    public record Row(string Id, string Name, string? Category, string? Address, double Lat, double Lon, float? Confidence);

    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    /// <summary>One line of the export, or null when it is not usable (no name letters, bad position).</summary>
    public static Row? Parse(string line)
    {
        if (string.IsNullOrWhiteSpace(line)) return null;
        var r = JsonSerializer.Deserialize<Row>(line, Json);
        if (r is null || string.IsNullOrWhiteSpace(r.Id) || string.IsNullOrWhiteSpace(r.Name)) return null;
        if (r.Lat is < -90 or > 90 || r.Lon is < -180 or > 180) return null;
        if (CommunityPlaceEndpoints.Key(r.Name).Length < 2) return null;
        return r with
        {
            Id = Cut(r.Id, 64)!, Name = Cut(r.Name.Trim(), 200)!, Category = Cut(r.Category, 60), Address = Cut(r.Address, 200),
        };
    }

    /// <summary>
    /// Removes duplicates: the same name (ignoring spaces, dashes, case) within 150 m, or one name
    /// that starts with the other within 40 m ("Crowne Plaza KL" / "Crowne Plaza KL, an IHG Hotel").
    /// The most confident record is kept; it takes a category from a duplicate if it has none.
    /// </summary>
    public static List<Row> Dedupe(IEnumerable<Row> rows)
    {
        const double cell = 0.002; // about 220 m
        var kept = new List<(Row Row, string Key)>();
        var grid = new Dictionary<(int, int), List<int>>();
        foreach (var r in rows.OrderByDescending(r => r.Confidence ?? 0f))
        {
            var key = CommunityPlaceEndpoints.Key(r.Name);
            var cx = (int)Math.Floor(r.Lon / cell);
            var cy = (int)Math.Floor(r.Lat / cell);
            var duplicateOf = -1;
            for (var dx = -1; dx <= 1 && duplicateOf < 0; dx++)
                for (var dy = -1; dy <= 1 && duplicateOf < 0; dy++)
                    if (grid.TryGetValue((cx + dx, cy + dy), out var near))
                        foreach (var i in near)
                        {
                            var (k, kKey) = kept[i];
                            var m = Metres(r.Lat, r.Lon, k.Lat, k.Lon);
                            var same = kKey == key && m <= 150;
                            var prefix = m <= 40 && Math.Min(kKey.Length, key.Length) >= 6 && (kKey.StartsWith(key) || key.StartsWith(kKey));
                            if (same || prefix) { duplicateOf = i; break; }
                        }
            if (duplicateOf >= 0)
            {
                if (kept[duplicateOf].Row.Category is null && r.Category is not null)
                    kept[duplicateOf] = (kept[duplicateOf].Row with { Category = r.Category }, kept[duplicateOf].Key);
                continue;
            }
            kept.Add((r, key));
            if (!grid.TryGetValue((cx, cy), out var list)) grid[(cx, cy)] = list = [];
            list.Add(kept.Count - 1);
        }
        return kept.Select(k => k.Row).ToList();
    }

    private static double Metres(double lat1, double lon1, double lat2, double lon2)
    {
        var k = Math.Cos(lat1 * Math.PI / 180);
        var dx = (lon2 - lon1) * k * 111_320;
        var dy = (lat2 - lat1) * 110_540;
        return Math.Sqrt(dx * dx + dy * dy);
    }

    public static async Task<int> ImportAsync(RoadPulseDb db, string path, ILogger log, CancellationToken ct = default)
    {
        var seenIds = new HashSet<string>();
        var parsed = File.ReadLines(path).Select(Parse).Where(r => r is not null && seenIds.Add(r.Id)).Select(r => r!).ToList();
        var rows = Dedupe(parsed);
        log.LogInformation("Read {Read} places, {Kept} after removing duplicates", parsed.Count, rows.Count);

        await db.Database.OpenConnectionAsync(ct);
        var conn = (NpgsqlConnection)db.Database.GetDbConnection();
        await using var tx = await conn.BeginTransactionAsync(ct);
        await using (var cmd = new NpgsqlCommand(
            "CREATE TEMP TABLE overture_import (id text, name text, category text, address text, search_key text, lat double precision, lon double precision, confidence real) ON COMMIT DROP",
            conn, tx))
            await cmd.ExecuteNonQueryAsync(ct);

        var count = 0;
        await using (var copy = await conn.BeginBinaryImportAsync(
            "COPY overture_import (id, name, category, address, search_key, lat, lon, confidence) FROM STDIN (FORMAT BINARY)", ct))
        {
            foreach (var r in rows)
            {
                await copy.StartRowAsync(ct);
                await copy.WriteAsync(r.Id, NpgsqlDbType.Text, ct);
                await copy.WriteAsync(r.Name, NpgsqlDbType.Text, ct);
                await WriteNullable(copy, r.Category, ct);
                await WriteNullable(copy, r.Address, ct);
                var key = CommunityPlaceEndpoints.Key(r.Name);
                await copy.WriteAsync(key.Length <= 200 ? key : key[..200], NpgsqlDbType.Text, ct);
                await copy.WriteAsync(r.Lat, NpgsqlDbType.Double, ct);
                await copy.WriteAsync(r.Lon, NpgsqlDbType.Double, ct);
                if (r.Confidence is { } c) await copy.WriteAsync(c, NpgsqlDbType.Real, ct); else await copy.WriteNullAsync(ct);
                count++;
            }
            await copy.CompleteAsync(ct);
        }

        await using (var cmd = new NpgsqlCommand("""
            DELETE FROM directory_places;
            INSERT INTO directory_places (id, name, category, address, search_key, location, confidence)
            SELECT id, name, category, address, search_key,
                   ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography, confidence
            FROM overture_import;
            """, conn, tx) { CommandTimeout = 1800 })
            await cmd.ExecuteNonQueryAsync(ct);
        await tx.CommitAsync(ct);
        await using (var analyze = new NpgsqlCommand("ANALYZE directory_places", conn)) await analyze.ExecuteNonQueryAsync(ct);
        log.LogInformation("Loaded {Count} Overture places", count);
        return count;
    }

    private static async Task WriteNullable(NpgsqlBinaryImporter copy, string? value, CancellationToken ct)
    {
        if (value is null) await copy.WriteNullAsync(ct); else await copy.WriteAsync(value, NpgsqlDbType.Text, ct);
    }

    private static string? Cut(string? s, int max) => s is null ? null : s.Length <= max ? s : s[..max];
}
