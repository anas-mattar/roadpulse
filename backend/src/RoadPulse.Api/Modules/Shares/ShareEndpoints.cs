using System.Collections.Concurrent;
using System.Security.Cryptography;
using Microsoft.AspNetCore.Http.HttpResults;
using RoadPulse.Api.Modules.Devices;

namespace RoadPulse.Api.Modules.Shares;

public enum ShareState { DRIVING, ARRIVED, STOPPED }

public record CreateShareRequest(string Destination, double DestLat, double DestLon, double Lat, double Lon, long EtaUnixMs, string? Name);
public record UpdateShareRequest(double Lat, double Lon, long EtaUnixMs, ShareState State);
public record CreateShareResponse(string Id, string Url, DateTimeOffset ExpiresAt);
/// <summary>What the person with the link sees. Nothing about the device.</summary>
public record ShareView(string? Name, string Destination, double DestLat, double DestLon, double Lat, double Lon,
    DateTimeOffset Eta, DateTimeOffset UpdatedAt, ShareState State);

/// <summary>One live "share my drive" link. Kept in memory only and forgotten soon after the drive.</summary>
public sealed class Share
{
    public required string Id { get; init; }
    public required Guid DeviceId { get; init; }
    public required string Destination { get; init; }
    public string? Name { get; init; }
    public double DestLat { get; init; }
    public double DestLon { get; init; }
    public double Lat { get; set; }
    public double Lon { get; set; }
    public DateTimeOffset Eta { get; set; }
    public DateTimeOffset UpdatedAt { get; set; }
    public DateTimeOffset ExpiresAt { get; set; }
    public ShareState State { get; set; }
}

/// <summary>
/// Live drive shares, in memory (a server restart ends them, which is fine for a link meant to
/// last one drive). A share ends 12 hours after it starts at the latest, 30 minutes after arrival,
/// and right away when the driver stops sharing.
/// </summary>
public sealed class ShareStore(TimeProvider clock)
{
    public static readonly TimeSpan MaxLifetime = TimeSpan.FromHours(12);
    public static readonly TimeSpan AfterArrival = TimeSpan.FromMinutes(30);
    public const int MaxPerDevice = 3;
    private readonly ConcurrentDictionary<string, Share> _shares = new();

    public DateTimeOffset Now => clock.GetUtcNow();

    public Share? Get(string id)
    {
        if (!_shares.TryGetValue(id, out var s)) return null;
        if (s.ExpiresAt <= Now || s.State == ShareState.STOPPED) { _shares.TryRemove(id, out _); return null; }
        return s;
    }

    public Share? Add(Share share)
    {
        Cleanup();
        if (_shares.Values.Count(s => s.DeviceId == share.DeviceId) >= MaxPerDevice) return null;
        _shares[share.Id] = share;
        return share;
    }

    public void Remove(string id) => _shares.TryRemove(id, out _);

    /// <summary>Ends every share of a device (used when the driver deletes their data).</summary>
    public void RemoveForDevice(Guid deviceId)
    {
        foreach (var (id, s) in _shares)
            if (s.DeviceId == deviceId) _shares.TryRemove(id, out _);
    }

    private void Cleanup()
    {
        foreach (var (id, s) in _shares)
            if (s.ExpiresAt <= Now || s.State == ShareState.STOPPED) _shares.TryRemove(id, out _);
    }

    /// <summary>Unguessable link id: 128 random bits, URL-safe.</summary>
    public static string NewId() =>
        Convert.ToBase64String(RandomNumberGenerator.GetBytes(16)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}

public static class ShareEndpoints
{
    public static RouteGroupBuilder MapShareEndpoints(this RouteGroupBuilder v1)
    {
        var shares = v1.MapGroup("/shares").WithTags("Share my drive");
        shares.MapPost("", Create).RequireAuthorization().RequireRateLimiting(RateLimits.Reports)
            .WithName("CreateShare").WithSummary("Starts sharing this drive; returns a link anyone can open.");
        shares.MapPut("/{id}", Update).RequireAuthorization().RequireRateLimiting(RateLimits.Votes)
            .WithName("UpdateShare").WithSummary("Updates position, arrival time and state (owner only).");
        shares.MapDelete("/{id}", Stop).RequireAuthorization()
            .WithName("StopShare").WithSummary("Stops sharing; the link stops working at once.");
        shares.MapGet("/{id}", View).RequireRateLimiting(RateLimits.Reads)
            .WithName("ViewShare").WithSummary("Public view of a shared drive (no device information).");
        return v1;
    }

    /// <summary>The page behind a share link (/s/{id}): plain HTML that polls the view endpoint.</summary>
    public static void MapSharePage(this WebApplication app)
    {
        var root = Path.Combine(app.Environment.ContentRootPath, "wwwroot", "share");
        static void Secure(HttpContext ctx)
        {
            var h = ctx.Response.Headers;
            h.ContentSecurityPolicy = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
            h.XContentTypeOptions = "nosniff";
            h["Referrer-Policy"] = "no-referrer";
            h.CacheControl = "no-store";
            h["X-Robots-Tag"] = "noindex";
        }
        app.MapGet("/s/{id}", (string id, HttpContext ctx) =>
        {
            Secure(ctx);
            return Results.File(Path.Combine(root, "index.html"), "text/html; charset=utf-8");
        }).ExcludeFromDescription();
        app.MapGet("/share-assets/{file}", (string file, HttpContext ctx) =>
        {
            Secure(ctx);
            return file switch
            {
                "share.js" => Results.File(Path.Combine(root, "share.js"), "text/javascript; charset=utf-8"),
                "share.css" => Results.File(Path.Combine(root, "share.css"), "text/css; charset=utf-8"),
                _ => Results.NotFound(),
            };
        }).ExcludeFromDescription();
    }

    private static string? Validate(double lat, double lon) =>
        lat is < -90 or > 90 || lon is < -180 or > 180 ? "Coordinates out of range." : null;

    private static async Task<Results<Created<CreateShareResponse>, ValidationProblem, Conflict<string>>> Create(
        CreateShareRequest r, HttpContext http, ShareStore store, IConfiguration config)
    {
        var errors = new Dictionary<string, string[]>();
        if (string.IsNullOrWhiteSpace(r.Destination) || r.Destination.Length > 120) errors["destination"] = ["1 to 120 characters."];
        if (r.Name is { Length: > 24 }) errors["name"] = ["At most 24 characters."];
        if ((Validate(r.Lat, r.Lon) ?? Validate(r.DestLat, r.DestLon)) is { } e) errors["position"] = [e];
        if (errors.Count > 0) return TypedResults.ValidationProblem(errors);
        var now = store.Now;
        var share = new Share
        {
            Id = ShareStore.NewId(), DeviceId = http.User.DeviceId(), Destination = r.Destination.Trim(),
            Name = string.IsNullOrWhiteSpace(r.Name) ? null : r.Name.Trim(),
            DestLat = r.DestLat, DestLon = r.DestLon, Lat = r.Lat, Lon = r.Lon,
            Eta = DateTimeOffset.FromUnixTimeMilliseconds(r.EtaUnixMs), UpdatedAt = now,
            ExpiresAt = now + ShareStore.MaxLifetime, State = ShareState.DRIVING,
        };
        if (store.Add(share) is null) return TypedResults.Conflict("Too many active shares; stop one first.");
        await Task.CompletedTask;
        var baseUrl = config["Share:PublicBaseUrl"]?.TrimEnd('/') ?? $"{http.Request.Scheme}://{http.Request.Host}";
        return TypedResults.Created($"/v1/shares/{share.Id}", new CreateShareResponse(share.Id, $"{baseUrl}/s/{share.Id}", share.ExpiresAt));
    }

    private static Results<NoContent, NotFound, ValidationProblem> Update(string id, UpdateShareRequest r, HttpContext http, ShareStore store)
    {
        var share = store.Get(id);
        if (share is null || share.DeviceId != http.User.DeviceId()) return TypedResults.NotFound();
        if (Validate(r.Lat, r.Lon) is { } e) return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["position"] = [e] });
        var now = store.Now;
        share.Lat = r.Lat;
        share.Lon = r.Lon;
        share.Eta = DateTimeOffset.FromUnixTimeMilliseconds(r.EtaUnixMs);
        share.UpdatedAt = now;
        if (r.State == ShareState.ARRIVED && share.State != ShareState.ARRIVED)
            share.ExpiresAt = Min(share.ExpiresAt, now + ShareStore.AfterArrival);
        share.State = r.State;
        if (r.State == ShareState.STOPPED) store.Remove(id);
        return TypedResults.NoContent();
    }

    private static Results<NoContent, NotFound> Stop(string id, HttpContext http, ShareStore store)
    {
        var share = store.Get(id);
        if (share is null || share.DeviceId != http.User.DeviceId()) return TypedResults.NotFound();
        store.Remove(id);
        return TypedResults.NoContent();
    }

    private static Results<Ok<ShareView>, NotFound> View(string id, ShareStore store) =>
        store.Get(id) is { } s
            ? TypedResults.Ok(new ShareView(s.Name, s.Destination, s.DestLat, s.DestLon, s.Lat, s.Lon, s.Eta, s.UpdatedAt, s.State))
            : TypedResults.NotFound();

    private static DateTimeOffset Min(DateTimeOffset a, DateTimeOffset b) => a < b ? a : b;
}
