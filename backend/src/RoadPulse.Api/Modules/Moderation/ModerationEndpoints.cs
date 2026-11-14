using System.Security.Claims;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Live;
using RoadPulse.Api.Modules.Reports;

namespace RoadPulse.Api.Modules.Moderation;

public record ModerationReason(string Reason);
public record BlockDeviceRequest(string Reason, bool RemoveActiveReports = true);
public record QueueItem(ReportDto Report, ReportStatus Status, int Flags, Dictionary<FlagReason, int> FlagReasons,
    Guid DeviceId, int DeviceReports, int DeviceRemovedReports, bool DeviceBlocked);
public record DeviceSummary(Guid Id, DateTimeOffset CreatedAt, bool Blocked, DateTimeOffset? BlockedAt,
    int Reports, int RemovedReports, int ActiveReports);
public record AuditDto(long Id, DateTimeOffset At, string Moderator, string Action, string TargetType, Guid TargetId, string Reason);

/// <summary>
/// Moderator API (plan section G): review queue, remove/restore reports, block/unblock devices, and
/// an audit log of every action. Every write needs a reason and is recorded.
/// </summary>
public static class ModerationEndpoints
{
    public const int MaxQueue = 200;

    public static RouteGroupBuilder MapModerationEndpoints(this RouteGroupBuilder v1)
    {
        var mod = v1.MapGroup("/moderation").WithTags("Moderation")
            .RequireAuthorization(ModeratorAuthHandler.Policy)
            .RequireRateLimiting(RateLimits.Moderation);

        mod.MapGet("/queue", Queue).WithName("ModerationQueue")
            .WithSummary("Unexpired reports hidden for review, then active reports with abuse flags, most flagged first.");
        mod.MapGet("/reports", RecentReports).WithName("RecentReports")
            .WithSummary("Newest reports in any status (limit 1-500), for removing reports nobody flagged.");
        mod.MapGet("/reports/{id:guid}", GetReport).WithName("GetReportForModeration");
        mod.MapPost("/reports/{id:guid}/remove", RemoveReport).WithName("RemoveReport");
        mod.MapPost("/reports/{id:guid}/restore", RestoreReport).WithName("RestoreReport")
            .WithSummary("Puts a report back on the map; later flags no longer hide it automatically.");
        mod.MapGet("/devices/{id:guid}", GetDevice).WithName("GetDevice");
        mod.MapPost("/devices/{id:guid}/block", BlockDevice).WithName("BlockDevice")
            .WithSummary("Blocks a device (its token stops working) and by default removes its active reports.");
        mod.MapPost("/devices/{id:guid}/unblock", UnblockDevice).WithName("UnblockDevice");
        mod.MapGet("/audit", Audit).WithName("AuditLog").WithSummary("Most recent moderator actions (limit 1-500).");
        return v1;
    }

    private static async Task<Ok<List<QueueItem>>> Queue(RoadPulseDb db, TimeProvider clock)
    {
        var now = clock.GetUtcNow();
        var reports = await db.Reports.AsNoTracking().Include(r => r.FlagEntries)
            // Only reports that would still be on the map: expired ones no longer need a decision
            // (they stay findable under recent reports and lookup).
            .Where(r => r.ExpiresAt > now
                        && (r.Status == ReportStatus.PendingReview || (r.Status == ReportStatus.Active && !r.Reviewed && r.Flags > 0)))
            .OrderByDescending(r => r.Status == ReportStatus.PendingReview).ThenByDescending(r => r.Flags).ThenBy(r => r.CreatedAt)
            .Take(MaxQueue)
            .ToListAsync();
        return TypedResults.Ok(await ToItems(db, reports));
    }

    private static async Task<Results<Ok<List<QueueItem>>, ValidationProblem>> RecentReports(RoadPulseDb db, int? limit)
    {
        var take = limit ?? 100;
        if (take is < 1 or > 500)
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["limit"] = ["Must be between 1 and 500."] });
        var reports = await db.Reports.AsNoTracking().Include(r => r.FlagEntries)
            .OrderByDescending(r => r.CreatedAt).Take(take).ToListAsync();
        return TypedResults.Ok(await ToItems(db, reports));
    }

    private static async Task<Results<Ok<QueueItem>, NotFound>> GetReport(Guid id, RoadPulseDb db)
    {
        var report = await db.Reports.AsNoTracking().Include(r => r.FlagEntries).FirstOrDefaultAsync(r => r.Id == id);
        if (report is null) return TypedResults.NotFound();
        return TypedResults.Ok((await ToItems(db, [report]))[0]);
    }

    /// <summary>Adds flag reasons and each reporter's history to a list of reports.</summary>
    private static async Task<List<QueueItem>> ToItems(RoadPulseDb db, List<Report> reports)
    {
        var deviceIds = reports.Select(r => r.DeviceId).Distinct().ToList();
        var stats = await db.Reports.AsNoTracking().Where(r => deviceIds.Contains(r.DeviceId))
            .GroupBy(r => r.DeviceId)
            .Select(g => new { g.Key, Total = g.Count(), Removed = g.Count(r => r.Status == ReportStatus.Removed) })
            .ToDictionaryAsync(x => x.Key);
        var blocked = await db.Devices.AsNoTracking().Where(d => deviceIds.Contains(d.Id) && d.Blocked).Select(d => d.Id).ToListAsync();

        return reports.Select(r => new QueueItem(
            ReportEndpoints.ToDto(r), r.Status, r.Flags,
            r.FlagEntries.GroupBy(f => f.Reason).ToDictionary(g => g.Key, g => g.Count()),
            r.DeviceId, stats[r.DeviceId].Total, stats[r.DeviceId].Removed, blocked.Contains(r.DeviceId))).ToList();
    }

    private static async Task<Results<NoContent, NotFound, ValidationProblem>> RemoveReport(
        Guid id, ModerationReason body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock, LiveReports live)
    {
        if (ReasonError(body.Reason) is { } error) return error;
        var report = await db.Reports.FindAsync(id);
        if (report is null) return TypedResults.NotFound();
        if (report.Status != ReportStatus.Removed)
        {
            report.Status = ReportStatus.Removed;
            Log(db, user, clock, "REMOVE_REPORT", "report", id, body.Reason);
            await db.SaveChangesAsync();
            await live.PublishAsync(report);
        }
        return TypedResults.NoContent();
    }

    private static async Task<Results<NoContent, NotFound, ValidationProblem>> RestoreReport(
        Guid id, ModerationReason body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock, LiveReports live)
    {
        if (ReasonError(body.Reason) is { } error) return error;
        var report = await db.Reports.FindAsync(id);
        if (report is null) return TypedResults.NotFound();
        report.Status = ReportStatus.Active;
        report.Reviewed = true;
        Log(db, user, clock, "RESTORE_REPORT", "report", id, body.Reason);
        await db.SaveChangesAsync();
        await live.PublishAsync(report);
        return TypedResults.NoContent();
    }

    private static async Task<Results<Ok<DeviceSummary>, NotFound>> GetDevice(Guid id, RoadPulseDb db, TimeProvider clock)
    {
        var device = await db.Devices.AsNoTracking().FirstOrDefaultAsync(d => d.Id == id);
        if (device is null) return TypedResults.NotFound();
        var now = clock.GetUtcNow();
        var mine = db.Reports.AsNoTracking().Where(r => r.DeviceId == id);
        return TypedResults.Ok(new DeviceSummary(device.Id, device.CreatedAt, device.Blocked, device.BlockedAt,
            await mine.CountAsync(),
            await mine.CountAsync(r => r.Status == ReportStatus.Removed),
            await mine.CountAsync(r => r.Status == ReportStatus.Active && r.ExpiresAt > now)));
    }

    private static async Task<Results<NoContent, NotFound, ValidationProblem>> BlockDevice(
        Guid id, BlockDeviceRequest body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock, LiveReports live)
    {
        if (ReasonError(body.Reason) is { } error) return error;
        var device = await db.Devices.FindAsync(id);
        if (device is null) return TypedResults.NotFound();
        var now = clock.GetUtcNow();
        if (!device.Blocked)
        {
            device.Blocked = true;
            device.BlockedAt = now;
            Log(db, user, clock, "BLOCK_DEVICE", "device", id, body.Reason);
        }
        var removed = new List<Report>();
        if (body.RemoveActiveReports)
        {
            removed = await db.Reports.Where(r => r.DeviceId == id && r.Status != ReportStatus.Removed && r.ExpiresAt > now).ToListAsync();
            foreach (var r in removed)
            {
                r.Status = ReportStatus.Removed;
                Log(db, user, clock, "REMOVE_REPORT", "report", r.Id, "Device blocked: " + body.Reason);
            }
        }
        await db.SaveChangesAsync();
        await live.PublishAsync(removed);
        return TypedResults.NoContent();
    }

    private static async Task<Results<NoContent, NotFound, ValidationProblem>> UnblockDevice(
        Guid id, ModerationReason body, ClaimsPrincipal user, RoadPulseDb db, TimeProvider clock, LiveReports live)
    {
        if (ReasonError(body.Reason) is { } error) return error;
        var device = await db.Devices.FindAsync(id);
        if (device is null) return TypedResults.NotFound();
        if (device.Blocked)
        {
            device.Blocked = false;
            device.BlockedAt = null;
            Log(db, user, clock, "UNBLOCK_DEVICE", "device", id, body.Reason);
            await db.SaveChangesAsync();
        }
        return TypedResults.NoContent();
    }

    private static async Task<Results<Ok<List<AuditDto>>, ValidationProblem>> Audit(RoadPulseDb db, int? limit, Guid? target)
    {
        var take = limit ?? 100;
        if (take is < 1 or > 500)
            return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["limit"] = ["Must be between 1 and 500."] });
        var q = db.AuditLog.AsNoTracking();
        if (target is { } t) q = q.Where(a => a.TargetId == t);
        var list = await q.OrderByDescending(a => a.Id).Take(take)
            .Select(a => new AuditDto(a.Id, a.At, a.Moderator, a.Action, a.TargetType, a.TargetId, a.Reason))
            .ToListAsync();
        return TypedResults.Ok(list);
    }

    private static ValidationProblem? ReasonError(string? reason) =>
        reason is null || reason.Trim().Length is < 3 or > 500
            ? TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["reason"] = ["A reason of 3-500 characters is required."] })
            : null;

    private static void Log(RoadPulseDb db, ClaimsPrincipal user, TimeProvider clock, string action, string targetType, Guid id, string reason) =>
        db.AuditLog.Add(new AuditEntry
        {
            At = clock.GetUtcNow(), Moderator = user.Identity?.Name ?? "unknown", Action = action,
            TargetType = targetType, TargetId = id, Reason = reason.Trim(),
        });
}
