using System.Globalization;
using System.Text;

namespace RoadPulse.Api.Modules.Tolls;

/// <summary>Vehicle class a toll price is for: CAR is Class 1, MOTORCYCLE is Class 0.</summary>
public enum TollVehicle { CAR, MOTORCYCLE }

/// <summary>How a toll plaza charges.</summary>
public enum TollSystem
{
    /// <summary>A fixed price at the plaza, whatever the trip (most Klang Valley highways).</summary>
    OPEN,
    /// <summary>The price depends on where you entered and left (PLUS and others). Not priced yet.</summary>
    CLOSED,
}

/// <summary>One row of tolls/toll-rates.csv. A null price means "not known".</summary>
public record TollRate(string Plaza, IReadOnlyList<string> Aliases, string? Highway, TollSystem System,
    decimal? CarRm, decimal? MotorcycleRm, string? Source, string? CheckedOn)
{
    public decimal? PriceFor(TollVehicle v) => v == TollVehicle.MOTORCYCLE ? MotorcycleRm : CarRm;
}

/// <summary>
/// The toll price table the owner keeps up to date (tolls/toll-rates.csv), and matching of
/// OpenStreetMap toll plaza names ("Plaza Tol Bukit Kiara", "Bukit Kiara Toll Plaza") to it.
/// Pure: unit tested.
/// </summary>
public sealed class TollRates
{
    public static readonly string[] Columns =
        ["plaza", "aliases", "highway", "system", "car_rm", "motorcycle_rm", "source", "checked_on", "notes"];

    private readonly Dictionary<string, List<TollRate>> _byKey = new();
    public IReadOnlyList<TollRate> All { get; }
    /// <summary>Rows that could not be read, with their line numbers (shown in the log).</summary>
    public IReadOnlyList<string> Problems { get; }

    private TollRates(List<TollRate> rates, List<string> problems)
    {
        All = rates;
        Problems = problems;
        foreach (var r in rates)
            foreach (var name in r.Aliases.Prepend(r.Plaza).Select(Normalise).Where(k => k.Length > 0).Distinct())
            {
                if (!_byKey.TryGetValue(name, out var list)) _byKey[name] = list = [];
                list.Add(r);
            }
    }

    public static readonly TollRates Empty = new([], []);

    /// <summary>Reads the CSV text. Lines starting with # and blank lines are ignored.</summary>
    public static TollRates Parse(string csv)
    {
        var rates = new List<TollRate>();
        var (rows, problems) = CsvTable.Read(csv, "plaza", "car_rm");
        foreach (var (lineNo, cell) in rows)
        {
            var plaza = cell("plaza");
            if (plaza is null) { problems.Add($"line {lineNo}: plaza name is empty"); continue; }
            if (!CsvTable.TryPrice(cell("car_rm"), out var car) || !CsvTable.TryPrice(cell("motorcycle_rm"), out var moto))
            {
                problems.Add($"line {lineNo} ({plaza}): prices must be numbers like 2.50, or empty when not known");
                continue;
            }
            var system = (cell("system") ?? "OPEN").ToUpperInvariant() switch
            {
                "OPEN" => TollSystem.OPEN,
                "CLOSED" => TollSystem.CLOSED,
                _ => (TollSystem?)null,
            };
            if (system is null) { problems.Add($"line {lineNo} ({plaza}): system must be OPEN or CLOSED"); continue; }
            var aliases = (cell("aliases") ?? "").Split('|', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);
            rates.Add(new TollRate(plaza, aliases, cell("highway"), system.Value, car, moto, cell("source"), cell("checked_on")));
        }
        return new(rates, problems);
    }

    /// <summary>
    /// The rate for an OpenStreetMap plaza name, or null when it is not in the table or the name
    /// matches rows of different highways (ambiguous: better unknown than a wrong price).
    /// </summary>
    public TollRate? Find(string? osmName)
    {
        var key = Normalise(osmName);
        if (key.Length == 0) return null;
        if (Unique(key) is { } exact) return exact;
        // "Plaza Tol Batu 9 (Arah Kajang)" → "batu 9"; "Damansara Northbound" → "damansara".
        var words = key.Split(' ').ToList();
        while (words.Count > 1 && DirectionWords.Contains(words[^1])) words.RemoveAt(words.Count - 1);
        var arah = words.FindIndex(w => w is "arah" or "hala" or "ke" or "to");
        if (arah > 0) words = words[..arah];
        var shorter = string.Join(' ', words);
        return shorter != key ? Unique(shorter) : null;
    }

    private TollRate? Unique(string key) =>
        _byKey.TryGetValue(key, out var list) && list.Select(r => (r.Highway ?? "").ToLowerInvariant()).Distinct().Count() == 1
            ? list[0]
            : null;

    private static readonly HashSet<string> DirectionWords =
        ["northbound", "southbound", "eastbound", "westbound", "nb", "sb", "eb", "wb", "masuk", "keluar", "entry", "exit"];

    private static readonly HashSet<string> Noise = ["plaza", "tol", "toll", "booth", "gate", "the"];

    /// <summary>Lower case, no brackets, punctuation or "Plaza Tol"/"Toll Plaza" words.</summary>
    public static string Normalise(string? name)
    {
        if (string.IsNullOrWhiteSpace(name)) return "";
        var sb = new StringBuilder();
        var depth = 0;
        foreach (var ch in name.ToLowerInvariant())
        {
            if (ch is '(' or '[') { depth++; continue; }
            if (ch is ')' or ']') { depth = Math.Max(0, depth - 1); sb.Append(' '); continue; }
            if (depth > 0) continue;
            sb.Append(char.IsLetterOrDigit(ch) ? ch : ' ');
        }
        return string.Join(' ', sb.ToString().Split(' ', StringSplitOptions.RemoveEmptyEntries).Where(w => !Noise.Contains(w)));
    }
}

/// <summary>Price of a trip on a closed-system highway (PLUS etc.), from tolls/toll-pairs.csv.</summary>
public record TollPair(string Highway, string Entry, string Exit, decimal? CarRm, decimal? MotorcycleRm, string? Source, string? CheckedOn)
{
    public decimal? PriceFor(TollVehicle v) => v == TollVehicle.MOTORCYCLE ? MotorcycleRm : CarRm;
}

/// <summary>
/// Closed-system prices by entry and exit plaza (tolls/toll-pairs.csv). A pair works in both
/// directions. Entry and exit are the plaza names used in toll-rates.csv (rows with system CLOSED).
/// </summary>
public sealed class TollPairs
{
    public static readonly string[] Columns = ["highway", "entry", "exit", "car_rm", "motorcycle_rm", "source", "checked_on", "notes"];
    private readonly Dictionary<(string, string, string), TollPair> _pairs = new();
    public IReadOnlyList<TollPair> All { get; }
    public IReadOnlyList<string> Problems { get; }
    public static readonly TollPairs Empty = new([], []);

    private TollPairs(List<TollPair> pairs, List<string> problems)
    {
        All = pairs;
        Problems = problems;
        foreach (var p in pairs)
        {
            var hw = TollRates.Normalise(p.Highway);
            _pairs[(hw, TollRates.Normalise(p.Entry), TollRates.Normalise(p.Exit))] = p;
            _pairs.TryAdd((hw, TollRates.Normalise(p.Exit), TollRates.Normalise(p.Entry)), p);
        }
    }

    public static TollPairs Parse(string csv)
    {
        var pairs = new List<TollPair>();
        var (rows, problems) = CsvTable.Read(csv, "entry", "exit", "car_rm");
        foreach (var (lineNo, cell) in rows)
        {
            var (hw, entry, exit) = (cell("highway") ?? "", cell("entry"), cell("exit"));
            if (entry is null || exit is null) { problems.Add($"line {lineNo}: entry and exit are needed"); continue; }
            if (!CsvTable.TryPrice(cell("car_rm"), out var car) || !CsvTable.TryPrice(cell("motorcycle_rm"), out var moto))
            {
                problems.Add($"line {lineNo} ({entry} - {exit}): prices must be numbers like 2.50, or empty");
                continue;
            }
            pairs.Add(new TollPair(hw, entry, exit, car, moto, cell("source"), cell("checked_on")));
        }
        return new(pairs, problems);
    }

    public TollPair? Find(string? highway, string entry, string exit) =>
        _pairs.GetValueOrDefault((TollRates.Normalise(highway), TollRates.Normalise(entry), TollRates.Normalise(exit)));
}

/// <summary>Minimal CSV reading shared by the toll tables (quotes, comments, Excel quirks).</summary>
internal static class CsvTable
{
    public static (List<(int Line, Func<string, string?> Cell)> Rows, List<string> Problems) Read(string csv, params string[] required)
    {
        var rows = new List<(int, Func<string, string?>)>();
        var problems = new List<string>();
        string[]? header = null;
        var lineNo = 0;
        foreach (var raw in csv.Split('\n'))
        {
            lineNo++;
            var line = raw.TrimEnd('\r').TrimStart('\uFEFF');
            // Excel may save comments as "# text",,,, or as an empty row ,,,,,
            if (line.Trim(' ', ',').Length == 0 || line.TrimStart(' ', '"').StartsWith('#')) continue;
            var cells = Split(line);
            if (header is null)
            {
                header = cells.Select(c => c.Trim().ToLowerInvariant()).ToArray();
                if (required.Any(r => !header.Contains(r)))
                    return ([], [$"line {lineNo}: the first row must be the header, with columns {string.Join(", ", required)}"]);
                continue;
            }
            var h = header;
            var row = cells;
            rows.Add((lineNo, name =>
            {
                var i = Array.IndexOf(h, name);
                var v = i >= 0 && i < row.Count ? row[i].Trim() : "";
                return v.Length == 0 ? null : v;
            }));
        }
        if (header is null) problems.Add("the file is empty");
        return (rows, problems);
    }

    public static bool TryPrice(string? s, out decimal? price)
    {
        price = null;
        if (s is null) return true;
        s = s.Replace("RM", "", StringComparison.OrdinalIgnoreCase).Trim();
        if (!decimal.TryParse(s, NumberStyles.Number, CultureInfo.InvariantCulture, out var v) || v < 0 || v > 1000) return false;
        price = v;
        return true;
    }

    private static List<string> Split(string line)
    {
        var cells = new List<string>();
        var sb = new StringBuilder();
        var quoted = false;
        for (var i = 0; i < line.Length; i++)
        {
            var ch = line[i];
            if (quoted)
            {
                if (ch == '"' && i + 1 < line.Length && line[i + 1] == '"') { sb.Append('"'); i++; }
                else if (ch == '"') quoted = false;
                else sb.Append(ch);
            }
            else if (ch == '"') quoted = true;
            else if (ch == ',') { cells.Add(sb.ToString()); sb.Clear(); }
            else sb.Append(ch);
        }
        cells.Add(sb.ToString());
        return cells;
    }
}

/// <summary>
/// Loads tolls/toll-rates.csv and reloads it whenever the file changes, so edited prices apply
/// without restarting the server. A missing file means "no prices" (every plaza shows as unknown).
/// </summary>
public sealed class TollRateStore(IConfiguration config, IHostEnvironment env, ILogger<TollRateStore> log)
{
    private readonly object _gate = new();
    private (DateTime Stamp, long Size, TollRates Rates)? _rates;
    private (DateTime Stamp, long Size, TollPairs Pairs)? _pairs;

    public string Path
    {
        get
        {
            var p = config["Tolls:RatesPath"] ?? "tolls/toll-rates.csv";
            return System.IO.Path.IsPathRooted(p) ? p : System.IO.Path.GetFullPath(System.IO.Path.Combine(env.ContentRootPath, p));
        }
    }

    /// <summary>toll-pairs.csv, next to toll-rates.csv.</summary>
    public string PairsPath => System.IO.Path.Combine(System.IO.Path.GetDirectoryName(Path) ?? ".", "toll-pairs.csv");

    public TollRates Current()
    {
        lock (_gate) return Load(Path, TollRates.Parse, r => (r.All.Count, r.Problems), ref _rates, TollRates.Empty, "toll prices");
    }

    public TollPairs CurrentPairs()
    {
        lock (_gate) return Load(PairsPath, TollPairs.Parse, r => (r.All.Count, r.Problems), ref _pairs, TollPairs.Empty, "closed-system toll prices");
    }

    private T Load<T>(string path, Func<string, T> parse, Func<T, (int Count, IReadOnlyList<string> Problems)> info,
        ref (DateTime Stamp, long Size, T Value)? cache, T empty, string what)
    {
        var file = new FileInfo(path);
        if (!file.Exists) return empty;
        if (cache is { } c && c.Stamp == file.LastWriteTimeUtc && c.Size == file.Length) return c.Value;
        T value;
        try
        {
            value = parse(File.ReadAllText(file.FullName));
        }
        catch (IOException e)
        {
            log.LogWarning("Could not read {What}: {Message}", what, e.Message);
            return cache is { } old ? old.Value : empty; // e.g. the file is open in Excel mid-save
        }
        var (count, problems) = info(value);
        log.LogInformation("Loaded {Count} {What}", count, what);
        foreach (var p in problems) log.LogWarning("{What}, skipped {Problem}", what, p);
        cache = (file.LastWriteTimeUtc, file.Length, value);
        return value;
    }
}
