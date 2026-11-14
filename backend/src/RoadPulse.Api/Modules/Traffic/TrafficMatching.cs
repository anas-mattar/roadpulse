using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace RoadPulse.Api.Modules.Traffic;

/// <summary>One GPS sample in an uploaded segment: seconds since the segment start, speed in m/s.</summary>
public record TrafficPoint(double Lat, double Lon, int T, double? SpeedMps);

/// <summary>A road edge the segment drove on, with the average measured speed there.</summary>
public record MatchedEdge(long EdgeId, long WayId, int ExpectedKph, IReadOnlyList<(double Lat, double Lon)> Line, double SpeedKph);

/// <summary>Matches a segment of GPS points to road edges (Valhalla trace_attributes in production).</summary>
public interface IMapMatcher
{
    Task<IReadOnlyList<MatchedEdge>> MatchAsync(IReadOnlyList<TrafficPoint> points, CancellationToken ct);
}

/// <summary>
/// Map matching with the local Valhalla server's trace_attributes. The points go to Valhalla and
/// are not kept anywhere. Speed per edge = average of the phone's GPS speeds on that edge, or
/// distance/time between the first and last point on it when the phone reports no speed.
/// </summary>
public sealed class ValhallaMatcher(HttpClient http, IConfiguration config, ILogger<ValhallaMatcher> log) : IMapMatcher
{
    public async Task<IReadOnlyList<MatchedEdge>> MatchAsync(IReadOnlyList<TrafficPoint> points, CancellationToken ct)
    {
        var url = (config["Traffic:ValhallaUrl"] ?? "http://valhalla:8002").TrimEnd('/') + "/trace_attributes";
        var t0 = 1_700_000_000; // relative times only: the real clock time is not sent
        var body = new
        {
            shape = points.Select(p => new { lat = p.Lat, lon = p.Lon, time = t0 + p.T }),
            costing = "auto",
            shape_match = "map_snap",
            filters = new
            {
                attributes = new[] { "edge.id", "edge.way_id", "edge.speed", "edge.begin_shape_index", "edge.end_shape_index",
                    "matched.edge_index", "matched.type", "shape" },
                action = "include",
            },
        };
        try
        {
            using var res = await http.PostAsJsonAsync(url, body, ct);
            if (!res.IsSuccessStatusCode) return [];
            var json = await res.Content.ReadAsStringAsync(ct);
            return Parse(json, points);
        }
        catch (Exception e) when (e is HttpRequestException or TaskCanceledException or JsonException)
        {
            log.LogWarning("Traffic map matching failed: {Error}", e.GetType().Name); // never log positions
            return [];
        }
    }

    private sealed record Response(
        [property: JsonPropertyName("shape")] string? Shape,
        [property: JsonPropertyName("edges")] List<Edge>? Edges,
        [property: JsonPropertyName("matched_points")] List<Matched>? MatchedPoints);
    private sealed record Edge(
        [property: JsonPropertyName("id")] long Id,
        [property: JsonPropertyName("way_id")] long WayId,
        [property: JsonPropertyName("speed")] int Speed,
        [property: JsonPropertyName("begin_shape_index")] int Begin,
        [property: JsonPropertyName("end_shape_index")] int End);
    private sealed record Matched(
        [property: JsonPropertyName("type")] string? Type,
        [property: JsonPropertyName("edge_index")] int? EdgeIndex);

    /// <summary>Turns a trace_attributes answer into edges with measured speeds. Pure: unit tested.</summary>
    public static IReadOnlyList<MatchedEdge> Parse(string json, IReadOnlyList<TrafficPoint> points)
    {
        var r = JsonSerializer.Deserialize<Response>(json);
        if (r?.Edges is null || r.MatchedPoints is null || r.Shape is null) return [];
        var line = DecodePolyline6(r.Shape);
        var byEdge = new Dictionary<int, List<int>>();
        for (var i = 0; i < r.MatchedPoints.Count && i < points.Count; i++)
        {
            var m = r.MatchedPoints[i];
            if (m.Type != "matched" || m.EdgeIndex is not { } e || e < 0 || e >= r.Edges.Count) continue;
            if (!byEdge.TryGetValue(e, out var list)) byEdge[e] = list = [];
            list.Add(i);
        }
        var result = new List<MatchedEdge>();
        foreach (var (e, idx) in byEdge)
        {
            var edge = r.Edges[e];
            var speeds = idx.Select(i => points[i].SpeedMps).Where(s => s is >= 0 and < 70).Select(s => s!.Value * 3.6).ToList();
            double kph;
            if (speeds.Count > 0) kph = speeds.Average();
            else if (idx.Count >= 2 && points[idx[^1]].T > points[idx[0]].T)
                kph = Metres(points[idx[0]], points[idx[^1]]) / (points[idx[^1]].T - points[idx[0]].T) * 3.6;
            else continue;
            var from = Math.Clamp(edge.Begin, 0, line.Count - 1);
            var to = Math.Clamp(edge.End, from, line.Count - 1);
            var geometry = line.Skip(from).Take(to - from + 1).ToList();
            if (geometry.Count < 2) continue;
            result.Add(new MatchedEdge(edge.Id, edge.WayId, edge.Speed, geometry, Math.Round(kph, 1)));
        }
        return result;
    }

    private static double Metres(TrafficPoint a, TrafficPoint b)
    {
        var k = Math.Cos(a.Lat * Math.PI / 180);
        return Math.Sqrt(Math.Pow((a.Lon - b.Lon) * k * 111_320, 2) + Math.Pow((a.Lat - b.Lat) * 110_540, 2));
    }

    /// <summary>Valhalla's encoded line (precision 6).</summary>
    public static List<(double Lat, double Lon)> DecodePolyline6(string s)
    {
        var points = new List<(double, double)>();
        int index = 0, lat = 0, lon = 0;
        while (index < s.Length)
        {
            lat += Next(s, ref index);
            lon += Next(s, ref index);
            points.Add((lat / 1e6, lon / 1e6));
        }
        return points;
    }

    private static int Next(string s, ref int index)
    {
        int result = 0, shift = 0, b;
        do
        {
            b = s[index++] - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < s.Length);
        return (result & 1) != 0 ? ~(result >> 1) : result >> 1;
    }
}
