using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;
using Microsoft.Extensions.Time.Testing;
using RoadPulse.Api.Data;

namespace RoadPulse.Api.Tests;

/// <summary>
/// Hosts the real API against a real PostgreSQL/PostGIS database (ROADPULSE_TEST_DB, or a local
/// default). The database is recreated from the migrations once per test run. Time is controlled by
/// a fake clock so expiry can be tested without waiting.
/// </summary>
public class ApiFactory : WebApplicationFactory<Program>
{
    public static string ConnectionString =>
        Environment.GetEnvironmentVariable("ROADPULSE_TEST_DB")
        ?? "Host=localhost;Database=roadpulse_test;Username=roadpulse;Password=dev-only-password";

    public FakeTimeProvider Clock { get; } = new(new DateTimeOffset(2026, 11, 14, 12, 0, 0, TimeSpan.Zero));

    /// <summary>Report limit per device per 10 minutes for this factory (tests lower it to exercise 429).</summary>
    public int ReportsPer10Minutes { get; init; } = 1000;

    /// <summary>Moderator key accepted by this test host (configuration holds only its hash).</summary>
    public const string ModeratorKey = "test-moderator-key-0123456789abcdef";

    /// <summary>Toll price table for this host (a temp file in toll tests).</summary>
    public string TollRatesPath { get; init; } = "/nonexistent/toll-rates.csv";

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseSetting("Tolls:RatesPath", TollRatesPath);
        builder.UseSetting("ConnectionStrings:Default", ConnectionString);
        builder.UseSetting("Database:MigrateOnStartup", "false");
        builder.UseSetting("RateLimits:ReadsPerMinute", "100000");
        builder.UseSetting("RateLimits:RegistrationsPerHour", "100000");
        builder.UseSetting("RateLimits:VotesPer10Minutes", "100000");
        builder.UseSetting("RateLimits:ReportsPer10Minutes", ReportsPer10Minutes.ToString());
        builder.UseSetting("RateLimits:ModerationPerMinute", "100000");
        builder.UseSetting("Moderation:Moderators:0:Name", "alice");
        builder.UseSetting("Moderation:Moderators:0:KeyHash", RoadPulse.Api.Modules.Devices.DeviceAuthHandler.HashToken(ModeratorKey));
        builder.ConfigureServices(s =>
        {
            s.RemoveAll<TimeProvider>();
            s.AddSingleton<TimeProvider>(Clock);
        });
    }

    private static readonly SemaphoreSlim Gate = new(1, 1);
    private static bool _migrated;

    /// <summary>Drops and re-creates the schema once per run, then returns a client.</summary>
    public async Task<HttpClient> ClientAsync()
    {
        await Gate.WaitAsync();
        try
        {
            if (!_migrated)
            {
                using var scope = Services.CreateScope();
                var db = scope.ServiceProvider.GetRequiredService<RoadPulseDb>();
                await db.Database.EnsureDeletedAsync();
                await db.Database.MigrateAsync();
                _migrated = true;
            }
        }
        finally
        {
            Gate.Release();
        }
        return CreateClient();
    }
}
