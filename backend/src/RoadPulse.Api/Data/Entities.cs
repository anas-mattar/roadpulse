using NetTopologySuite.Geometries;

namespace RoadPulse.Api.Data;

/// <summary>Anonymous app installation (ADR 0006). Only a hash of its secret token is stored.</summary>
public class Device
{
    public Guid Id { get; set; }
    public required string TokenHash { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public bool Blocked { get; set; }
    public DateTimeOffset? BlockedAt { get; set; }
}

/// <summary>Report categories; names match the Android app's ReportType enum.</summary>
public enum ReportType { POLICE, CRASH, HAZARD, TRAFFIC_JAM, ROAD_CLOSED }

/// <summary>Active: shown on the map. PendingReview: hidden after enough abuse flags until a
/// moderator decides. Removed: taken down by a moderator.</summary>
public enum ReportStatus { Active, PendingReview, Removed }

/// <summary>Why a driver flags a report as abuse (separate from "not there" votes).</summary>
public enum FlagReason { FAKE, SPAM, OFFENSIVE, WRONG_LOCATION }

public class Report
{
    public Guid Id { get; set; }
    public ReportType Type { get; set; }
    /// <summary>WGS84 point stored as PostGIS geography (distances in metres).</summary>
    public required Point Location { get; set; }
    public short? HeadingDeg { get; set; }
    public Guid DeviceId { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
    public DateTimeOffset ExpiresAt { get; set; }
    public int Confirmations { get; set; }
    public int Dismissals { get; set; }
    public ReportStatus Status { get; set; } = ReportStatus.Active;
    public int Flags { get; set; }
    /// <summary>Set when a moderator restores a report; later flags no longer auto-hide it.</summary>
    public bool Reviewed { get; set; }
    public List<ReportVote> Votes { get; set; } = new();
    public List<ReportFlag> FlagEntries { get; set; } = new();
}

/// <summary>One abuse flag per device per report.</summary>
public class ReportFlag
{
    public Guid ReportId { get; set; }
    public Guid DeviceId { get; set; }
    public FlagReason Reason { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
}

/// <summary>Append-only record of every moderator action. Holds IDs only, no locations or tokens.</summary>
public class AuditEntry
{
    public long Id { get; set; }
    public DateTimeOffset At { get; set; }
    public required string Moderator { get; set; }
    public required string Action { get; set; }
    public required string TargetType { get; set; }
    public Guid TargetId { get; set; }
    public required string Reason { get; set; }
}

/// <summary>One vote per device per report ("still there" or "not there").</summary>
public class ReportVote
{
    public Guid ReportId { get; set; }
    public Guid DeviceId { get; set; }
    public bool StillThere { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
}

/// <summary>Remembers write requests by Idempotency-Key so retries do not create duplicates.</summary>
public class IdempotencyRecord
{
    public Guid DeviceId { get; set; }
    public required string Key { get; set; }
    public Guid ReportId { get; set; }
    public DateTimeOffset CreatedAt { get; set; }
}
