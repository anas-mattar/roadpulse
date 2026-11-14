using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;
using RoadPulse.Api.Data;

namespace RoadPulse.Api.Modules.Devices;

/// <summary>
/// "Authorization: Bearer &lt;device token&gt;" authentication for anonymous devices (ADR 0006).
/// The token is a random secret issued once at registration; only its SHA-256 hash is stored.
/// </summary>
public class DeviceAuthHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder,
    RoadPulseDb db) : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    public const string SchemeName = "Device";

    protected override async Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var header = Request.Headers.Authorization.ToString();
        if (!header.StartsWith("Bearer ", StringComparison.Ordinal)) return AuthenticateResult.NoResult();
        var token = header["Bearer ".Length..].Trim();
        if (token.Length is < 20 or > 200) return AuthenticateResult.Fail("Invalid token");

        var hash = HashToken(token);
        var device = await db.Devices.AsNoTracking().FirstOrDefaultAsync(d => d.TokenHash == hash);
        if (device is null || device.Blocked) return AuthenticateResult.Fail("Unknown or blocked device");

        var identity = new ClaimsIdentity([new Claim(ClaimTypes.NameIdentifier, device.Id.ToString())], SchemeName);
        return AuthenticateResult.Success(new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName));
    }

    public static string NewToken() => Base64Url(RandomNumberGenerator.GetBytes(32));

    public static string HashToken(string token) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

    private static string Base64Url(byte[] bytes) => Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}

public static class DeviceClaims
{
    public static Guid DeviceId(this ClaimsPrincipal user) => Guid.Parse(user.FindFirstValue(ClaimTypes.NameIdentifier)!);
}
