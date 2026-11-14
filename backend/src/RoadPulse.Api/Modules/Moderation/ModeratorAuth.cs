using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using Microsoft.AspNetCore.Authentication;
using Microsoft.Extensions.Options;
using RoadPulse.Api.Modules.Devices;

namespace RoadPulse.Api.Modules.Moderation;

/// <summary>A moderator account from configuration: a display name and the SHA-256 hash of their key.</summary>
public class ModeratorAccount
{
    public string Name { get; set; } = "";
    public string KeyHash { get; set; } = "";
}

public class ModerationOptions
{
    public List<ModeratorAccount> Moderators { get; set; } = new();
}

/// <summary>
/// "X-Moderator-Key: &lt;key&gt;" authentication for the moderation API. Keys live outside the code and
/// database (environment variables or a secrets file); configuration holds only their SHA-256 hashes,
/// compared in constant time. Kept separate from device tokens so a device can never moderate.
/// </summary>
public class ModeratorAuthHandler(
    IOptionsMonitor<AuthenticationSchemeOptions> options,
    ILoggerFactory logger,
    UrlEncoder encoder,
    IOptionsMonitor<ModerationOptions> moderation) : AuthenticationHandler<AuthenticationSchemeOptions>(options, logger, encoder)
{
    public const string SchemeName = "Moderator";
    public const string Header = "X-Moderator-Key";
    public const string Policy = "Moderator";

    protected override Task<AuthenticateResult> HandleAuthenticateAsync()
    {
        var key = Request.Headers[Header].ToString();
        if (key.Length == 0) return Task.FromResult(AuthenticateResult.NoResult());
        if (key.Length is < 20 or > 200) return Task.FromResult(AuthenticateResult.Fail("Invalid key"));

        var hash = Encoding.ASCII.GetBytes(DeviceAuthHandler.HashToken(key));
        var match = moderation.CurrentValue.Moderators.FirstOrDefault(m =>
            m.KeyHash.Length == 64 && CryptographicOperations.FixedTimeEquals(hash, Encoding.ASCII.GetBytes(m.KeyHash.ToUpperInvariant())));
        if (match is null) return Task.FromResult(AuthenticateResult.Fail("Unknown moderator key"));

        var identity = new ClaimsIdentity(
            [new Claim(ClaimTypes.Name, match.Name), new Claim(ClaimTypes.Role, "moderator")], SchemeName);
        return Task.FromResult(AuthenticateResult.Success(new AuthenticationTicket(new ClaimsPrincipal(identity), SchemeName)));
    }
}
