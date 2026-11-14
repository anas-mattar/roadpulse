using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Live;
using RoadPulse.Api.Modules.Shares;

namespace RoadPulse.Api.Modules.Privacy;

public record ExportedReport(Guid Id, ReportType Type, string? Subtype, double Lat, double Lon, DateTimeOffset CreatedAt,
    DateTimeOffset ExpiresAt, ReportStatus Status, int Confirmations, int Dismissals);
public record ExportedVote(Guid ReportId, bool StillThere, DateTimeOffset CreatedAt);
public record ExportedFlag(Guid ReportId, FlagReason Reason, DateTimeOffset CreatedAt);
/// <summary>Everything the server holds about one device (PDPA access request). Traffic samples are
/// anonymous and not linked to devices, so there is nothing to export for them.</summary>
public record DataExport(Guid DeviceId, DateTimeOffset RegisteredAt, string? Nickname, bool Blocked,
    List<ExportedReport> Reports, List<ExportedVote> Votes, List<ExportedFlag> Flags, string Note);

/// <summary>How long data is kept. Pure values, shown in the privacy policy too.</summary>
public static class Retention
{
    /// <summary>Reports (with their votes and flags) are deleted this long after they expire.</summary>
    public static readonly TimeSpan Reports = TimeSpan.FromDays(90);
    public static readonly TimeSpan IdempotencyKeys = TimeSpan.FromDays(2);
    public static readonly TimeSpan ModerationLog = TimeSpan.FromDays(365);
}

/// <summary>
/// The driver's data rights (PDPA): see a copy of everything linked to this device, and delete it.
/// Deleting removes the device's reports, votes, flags, nickname, live share links and the device
/// itself; the app then forgets its token. Moderation log entries keep only ids (no personal data).
/// </summary>
public static class PrivacyEndpoints
{
    public static RouteGroupBuilder MapPrivacyEndpoints(this RouteGroupBuilder v1)
    {
        var me = v1.MapGroup("/me").WithTags("Privacy").RequireAuthorization();
        me.MapGet("/export", Export).RequireRateLimiting(RateLimits.Votes)
            .WithName("ExportMyData").WithSummary("A copy of all data linked to this device.");
        me.MapDelete("", Delete).RequireRateLimiting(RateLimits.Votes)
            .WithName("DeleteMyData").WithSummary("Deletes this device and everything linked to it.");
        return v1;
    }

    /// <summary>The privacy policy page (/privacy).</summary>
    public static void MapPrivacyPage(this WebApplication app)
    {
        var file = Path.Combine(app.Environment.ContentRootPath, "wwwroot", "privacy", "index.html");
        app.MapGet("/privacy", (HttpContext ctx) =>
        {
            var h = ctx.Response.Headers;
            h.ContentSecurityPolicy = "default-src 'none'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
            h.XContentTypeOptions = "nosniff";
            h["Referrer-Policy"] = "no-referrer";
            return Results.File(file, "text/html; charset=utf-8");
        }).ExcludeFromDescription();
        app.MapGet("/privacy/privacy.css", () =>
            Results.File(Path.Combine(app.Environment.ContentRootPath, "wwwroot", "privacy", "privacy.css"), "text/css; charset=utf-8"))
            .ExcludeFromDescription();
    }

    private static async Task<Ok<DataExport>> Export(HttpContext http, RoadPulseDb db)
    {
        var id = http.User.DeviceId();
        var device = await db.Devices.AsNoTracking().FirstAsync(d => d.Id == id);
        var reports = (await db.Reports.AsNoTracking().Where(r => r.DeviceId == id).OrderBy(r => r.CreatedAt).ToListAsync())
            .Select(r => new ExportedReport(r.Id, r.Type, r.Subtype, r.Location.Y, r.Location.X, r.CreatedAt, r.ExpiresAt, r.Status, r.Confirmations, r.Dismissals))
            .ToList();
        var votes = await db.ReportVotes.AsNoTracking().Where(v => v.DeviceId == id).OrderBy(v => v.CreatedAt)
            .Select(v => new ExportedVote(v.ReportId, v.StillThere, v.CreatedAt)).ToListAsync();
        var flags = await db.ReportFlags.AsNoTracking().Where(f => f.DeviceId == id).OrderBy(f => f.CreatedAt)
            .Select(f => new ExportedFlag(f.ReportId, f.Reason, f.CreatedAt)).ToListAsync();
        return TypedResults.Ok(new DataExport(id, device.CreatedAt, device.Nickname, device.Blocked, reports, votes, flags,
            "Live traffic samples are anonymous and not linked to this device; only road speed averages are kept."));
    }

    private static async Task<NoContent> Delete(HttpContext http, RoadPulseDb db, ShareStore shares, LiveReports live)
    {
        var id = http.User.DeviceId();
        await using var tx = await db.Database.BeginTransactionAsync();

        // Votes and flags this device gave on other reports: remove them and correct the counts.
        var voted = await db.ReportVotes.Where(v => v.DeviceId == id).Select(v => new { v.ReportId, v.StillThere }).ToListAsync();
        foreach (var v in voted)
        {
            if (v.StillThere)
                await db.Reports.Where(r => r.Id == v.ReportId && r.Confirmations > 0).ExecuteUpdateAsync(u => u.SetProperty(r => r.Confirmations, r => r.Confirmations - 1));
            else
                await db.Reports.Where(r => r.Id == v.ReportId && r.Dismissals > 0).ExecuteUpdateAsync(u => u.SetProperty(r => r.Dismissals, r => r.Dismissals - 1));
        }
        await db.ReportVotes.Where(v => v.DeviceId == id).ExecuteDeleteAsync();
        var flagged = await db.ReportFlags.Where(f => f.DeviceId == id).Select(f => f.ReportId).ToListAsync();
        foreach (var r in flagged)
            await db.Reports.Where(x => x.Id == r && x.Flags > 0).ExecuteUpdateAsync(u => u.SetProperty(x => x.Flags, x => x.Flags - 1));
        await db.ReportFlags.Where(f => f.DeviceId == id).ExecuteDeleteAsync();

        // This device's own reports (others' votes on them go too), then the device.
        var own = await db.Reports.Where(r => r.DeviceId == id).ToListAsync();
        await db.IdempotencyRecords.Where(i => i.DeviceId == id).ExecuteDeleteAsync();
        await db.Reports.Where(r => r.DeviceId == id).ExecuteDeleteAsync();
        await db.Devices.Where(d => d.Id == id).ExecuteDeleteAsync();
        await tx.CommitAsync();

        shares.RemoveForDevice(id);
        foreach (var r in own) r.Status = ReportStatus.Removed; // tell map viewers they are gone
        await live.PublishAsync(own);
        return TypedResults.NoContent();
    }
}

/// <summary>Deletes data past its retention period (see <see cref="Retention"/>), once an hour.</summary>
public sealed class PrivacyRetention(IServiceScopeFactory scopes, TimeProvider clock, ILogger<PrivacyRetention> log) : BackgroundService
{
    public static async Task<int> RunOnceAsync(RoadPulseDb db, DateTimeOffset now, CancellationToken ct = default)
    {
        var reportCutoff = now - Retention.Reports;
        var n = await db.Reports.Where(r => r.ExpiresAt < reportCutoff && r.Status != ReportStatus.PendingReview).ExecuteDeleteAsync(ct);
        var keyCutoff = now - Retention.IdempotencyKeys;
        n += await db.IdempotencyRecords.Where(i => i.CreatedAt < keyCutoff).ExecuteDeleteAsync(ct);
        var auditCutoff = now - Retention.ModerationLog;
        n += await db.AuditLog.Where(a => a.At < auditCutoff).ExecuteDeleteAsync(ct);
        return n;
    }

    protected override async Task ExecuteAsync(CancellationToken stop)
    {
        while (!stop.IsCancellationRequested)
        {
            try
            {
                using var scope = scopes.CreateScope();
                var n = await RunOnceAsync(scope.ServiceProvider.GetRequiredService<RoadPulseDb>(), clock.GetUtcNow(), stop);
                if (n > 0) log.LogInformation("Retention: deleted {Count} old rows", n);
            }
            catch (Exception e) when (e is not OperationCanceledException)
            {
                log.LogWarning("Retention run failed: {Error}", e.GetType().Name);
            }
            await Task.Delay(TimeSpan.FromHours(1), stop).ContinueWith(_ => { });
        }
    }
}
