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
            .WithSummary("Named places (Overture Maps) whose name contains q, within about 100 km of lat/lon: names starting with q first, then nearest.");
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

        // Two steps, both cheap (a slow search here makes search-while-typing feel stuck):
        //  1. Only the 5,000 places nearest to the driver (the map index finds those quickly). Common
        //     words ("kl", "restoran", "99") have plenty of matches there, so this is the whole answer.
        //  2. Otherwise every match in a box of about 100 km around the driver. The name index
        //     makes this quick for the less common words that reach this step.
        // Order in both: names starting with the text first; then nearest, where a place Overture is
        // unsure about (confidence under 0.5) counts as three times as far away. "Nearest" uses flat
        // map distance, which is much cheaper than distance on the globe and the same order this
        // close to the equator; the true distance in metres is worked out only for the rows returned.
        var near = await Query(db, key, lat!.Value, lon!.Value, take, NearestFirst);
        if (near.Count < take || (key.Length >= 3 && !near.Any(h => h.Starts)))
            near = await Query(db, key, lat.Value, lon.Value, take, nearestOnly: 0);
        return TypedResults.Ok(near.Select(h => new DirectoryPlaceDto(
            h.Id, h.Name, h.Category, h.Address, h.Lat, h.Lon, Math.Round(h.DistanceM))).ToList());
    }

    /// <summary>How many of the nearest places step 1 looks at.</summary>
    public const int NearestFirst = 5000;
    /// <summary>Half the side of the search box in degrees (about 100 km).</summary>
    private const double BoxDegrees = 0.9;

    private sealed record Hit(string Id, string Name, string? Category, string? Address, double Lat, double Lon, double DistanceM, bool Starts);

    private static Task<List<Hit>> Query(RoadPulseDb db, string key, double lat, double lon, int take, int nearestOnly)
    {
        // key holds only letters and digits (CommunityPlaceEndpoints.Key), so it needs no LIKE escaping.
        var contains = "%" + key + "%";
        var prefix = key + "%";
        var source = nearestOnly > 0
            ? $"(SELECT * FROM directory_places ORDER BY location <-> ST_SetSRID(ST_MakePoint({{0}}, {{1}}), 4326)::geography LIMIT {nearestOnly})"
            : "directory_places";
        // Column names follow the database naming (snake_case), which is how they map onto Hit.
        var sql = $"""
            SELECT m.id, m.name, m.category, m.address,
                   ST_Y(m.location::geometry) AS lat, ST_X(m.location::geometry) AS lon,
                   ST_Distance(m.location, ST_SetSRID(ST_MakePoint({"{0}"}, {"{1}"}), 4326)::geography) AS distance_m,
                   m.starts
            FROM (
                SELECT p.*, (p.search_key LIKE {"{3}"}) AS starts
                FROM {source} p
                WHERE p.search_key LIKE {"{2}"}
                  AND p.location::geometry && ST_Expand(ST_SetSRID(ST_MakePoint({"{0}"}, {"{1}"}), 4326), {"{4}"})
                ORDER BY (p.search_key LIKE {"{3}"}) DESC,
                         ST_Distance(p.location::geometry, ST_SetSRID(ST_MakePoint({"{0}"}, {"{1}"}), 4326))
                             * (CASE WHEN p.confidence < {"{5}"} THEN 3 ELSE 1 END)
                LIMIT {"{6}"}
            ) m
            ORDER BY m.starts DESC,
                     ST_Distance(m.location::geometry, ST_SetSRID(ST_MakePoint({"{0}"}, {"{1}"}), 4326))
                         * (CASE WHEN m.confidence < {"{5}"} THEN 3 ELSE 1 END)
            """;
        return db.Database.SqlQueryRaw<Hit>(sql, lon, lat, contains, prefix, BoxDegrees, LowConfidence, take).ToListAsync();
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
            conn, tx) { CommandTimeout = 300 })
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

        // Loading a million rows into a table that already has its search indexes is very slow
        // (over 30 minutes on a laptop). Empty the table, remove the indexes, load, then build
        // the indexes once at the end. All inside one transaction: a failure leaves the old data.
        await using (var cmd = new NpgsqlCommand("""
            TRUNCATE directory_places;
            DROP INDEX IF EXISTS ix_directory_places_location;
            DROP INDEX IF EXISTS ix_directory_places_search_key;
            INSERT INTO directory_places (id, name, category, address, search_key, location, confidence)
            SELECT id, name, category, address, search_key,
                   ST_SetSRID(ST_MakePoint(lon, lat), 4326)::geography, confidence
            FROM overture_import;
            CREATE INDEX ix_directory_places_location ON directory_places USING gist (location);
            CREATE INDEX ix_directory_places_search_key ON directory_places USING gin (search_key gin_trgm_ops);
            """, conn, tx) { CommandTimeout = 0 })
            await cmd.ExecuteNonQueryAsync(ct);
        await tx.CommitAsync(ct);
        log.LogInformation("Loaded {Count} Overture places", count);
        // Statistics for fast searches. Slow on some laptops (Docker disk): allow time, and if it
        // still fails the places are loaded anyway (PostgreSQL also updates statistics by itself).
        try
        {
            await using var analyze = new NpgsqlCommand("ANALYZE directory_places", conn) { CommandTimeout = 0 };
            await analyze.ExecuteNonQueryAsync(ct);
        }
        catch (Exception e) when (e is NpgsqlException or TimeoutException)
        {
            log.LogWarning("Places are loaded, but updating search statistics failed ({Error}); searches may be slower for a while.", e.Message);
        }
        return count;
    }

    private static async Task WriteNullable(NpgsqlBinaryImporter copy, string? value, CancellationToken ct)
    {
        if (value is null) await copy.WriteNullAsync(ct); else await copy.WriteAsync(value, NpgsqlDbType.Text, ct);
    }

    private static string? Cut(string? s, int max) => s is null ? null : s.Length <= max ? s : s[..max];
}
