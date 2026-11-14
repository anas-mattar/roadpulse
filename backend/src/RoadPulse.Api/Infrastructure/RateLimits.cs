using System.Security.Claims;
using System.Threading.RateLimiting;
using Microsoft.AspNetCore.RateLimiting;

namespace RoadPulse.Api;

/// <summary>Per-device (writes) and per-IP (reads, registration) limits. Values come from configuration.</summary>
public static class RateLimits
{
    public const string Reads = "reads";
    public const string Reports = "reports";
    public const string Votes = "votes";
    public const string Registration = "registration";
    public const string Moderation = "moderation";

    public static IServiceCollection AddRoadPulseRateLimits(this IServiceCollection services, IConfiguration config)
    {
        int Limit(string name, int fallback) => config.GetValue($"RateLimits:{name}", fallback);

        services.AddRateLimiter(o =>
        {
            o.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
            o.AddPolicy(Reads, http => Fixed(ClientIp(http), Limit("ReadsPerMinute", 120), TimeSpan.FromMinutes(1)));
            o.AddPolicy(Registration, http => Fixed(ClientIp(http), Limit("RegistrationsPerHour", 10), TimeSpan.FromHours(1)));
            o.AddPolicy(Reports, http => Fixed(DeviceOrIp(http), Limit("ReportsPer10Minutes", 10), TimeSpan.FromMinutes(10)));
            o.AddPolicy(Moderation, http => Fixed(ClientIp(http), Limit("ModerationPerMinute", 120), TimeSpan.FromMinutes(1)));
            o.AddPolicy(Votes, http => Fixed(DeviceOrIp(http), Limit("VotesPer10Minutes", 60), TimeSpan.FromMinutes(10)));
        });
        return services;
    }

    private static RateLimitPartition<string> Fixed(string key, int limit, TimeSpan window) =>
        RateLimitPartition.GetFixedWindowLimiter(key, _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = limit, Window = window, QueueLimit = 0,
        });

    private static string ClientIp(HttpContext http) => "ip:" + (http.Connection.RemoteIpAddress?.ToString() ?? "unknown");

    private static string DeviceOrIp(HttpContext http) =>
        http.User.FindFirstValue(ClaimTypes.NameIdentifier) is { } id ? "device:" + id : ClientIp(http);
}
