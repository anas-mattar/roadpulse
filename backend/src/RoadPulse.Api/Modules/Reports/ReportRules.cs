using RoadPulse.Api.Data;

namespace RoadPulse.Api.Modules.Reports;

/// <summary>Lifecycle rules for community reports (plan Phase 2). Pure functions: unit tested.</summary>
public static class ReportRules
{
    /// <summary>Same type within this distance and time counts as the same report.</summary>
    public const double DuplicateRadiusMeters = 100;
    public static readonly TimeSpan DuplicateWindow = TimeSpan.FromMinutes(15);

    /// <summary>How long a new report stays visible without votes.</summary>
    public static TimeSpan TimeToLive(ReportType type) => type switch
    {
        ReportType.TRAFFIC_JAM => TimeSpan.FromMinutes(30),
        ReportType.POLICE => TimeSpan.FromMinutes(45),
        ReportType.CRASH => TimeSpan.FromMinutes(60),
        ReportType.HAZARD => TimeSpan.FromMinutes(90),
        ReportType.ROAD_CLOSED => TimeSpan.FromHours(6),
        _ => TimeSpan.FromMinutes(30),
    };

    /// <summary>A report never lives longer than this, however many confirmations it gets.</summary>
    public static TimeSpan MaxLifetime(ReportType type) => TimeToLive(type) * 4;

    /// <summary>A "still there" vote pushes expiry to at least now + half the TTL, within the max lifetime.</summary>
    public static DateTimeOffset ExtendOnConfirm(ReportType type, DateTimeOffset createdAt, DateTimeOffset expiresAt, DateTimeOffset now)
    {
        var extended = now + TimeToLive(type) / 2;
        var cap = createdAt + MaxLifetime(type);
        var result = extended > expiresAt ? extended : expiresAt;
        return result < cap ? result : cap;
    }

    /// <summary>Removed when at least two people say it is gone and they outnumber confirmations.</summary>
    public static bool ShouldRemove(int confirmations, int dismissals) => dismissals >= 2 && dismissals > confirmations;

    /// <summary>Abuse flags from different devices that hide a report until a moderator reviews it.</summary>
    public const int DefaultAutoHideFlags = 3;

    /// <summary>Hide for review once enough devices flag it, unless a moderator already restored it.</summary>
    public static bool ShouldHideForReview(int flags, bool reviewed, int threshold) => !reviewed && flags >= threshold;
}
