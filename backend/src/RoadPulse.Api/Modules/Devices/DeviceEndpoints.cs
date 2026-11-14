using RoadPulse.Api.Data;

namespace RoadPulse.Api.Modules.Devices;

public record DeviceRegistration(Guid DeviceId, string Token);

public static class DeviceEndpoints
{
    public static RouteGroupBuilder MapDeviceEndpoints(this RouteGroupBuilder v1)
    {
        // Anonymous registration: no personal data. The app stores the token and sends it as a
        // bearer token on writes. Rate limited per IP to slow down mass registration.
        v1.MapPost("/devices", async (RoadPulseDb db, TimeProvider clock) =>
            {
                var token = DeviceAuthHandler.NewToken();
                var device = new Device { Id = Guid.NewGuid(), TokenHash = DeviceAuthHandler.HashToken(token), CreatedAt = clock.GetUtcNow() };
                db.Devices.Add(device);
                await db.SaveChangesAsync();
                return TypedResults.Created($"/v1/devices/{device.Id}", new DeviceRegistration(device.Id, token));
            })
            .RequireRateLimiting(RateLimits.Registration)
            .WithName("RegisterDevice")
            .WithSummary("Registers an anonymous device and returns its secret token (shown once).");
        return v1;
    }
}
