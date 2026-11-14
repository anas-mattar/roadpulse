using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Http.HttpResults;
using Microsoft.EntityFrameworkCore;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;

namespace RoadPulse.Api.Modules.Profile;

public record ProfileStats(int Reports, int ConfirmationsReceived, int Votes, int Removed);
public record ProfileDto(string? Nickname, int Points, string Level, int LevelIndex, int? NextLevelPoints,
    ProfileStats Stats, List<string> Badges);
public record UpdateProfileRequest(string? Nickname);

/// <summary>
/// Points, levels and badges for community reporting. Pure: unit tested. Points reward reports
/// that other drivers confirm, so spamming unconfirmed reports earns little and removed reports cost.
/// </summary>
public static class ProfileRules
{
    public const int PerReport = 10;
    public const int PerConfirmationReceived = 5;
    public const int PerVote = 2;
    public const int PerRemovedReport = -20;

    /// <summary>Levels and the points needed (original names, not another app's).</summary>
    public static readonly (string Name, int From)[] Levels =
    [
        ("LEARNER", 0), ("ROAD_SCOUT", 100), ("NAVIGATOR", 500), ("ROAD_GUARDIAN", 2_000), ("ROAD_LEGEND", 10_000),
    ];

    public static int Points(ProfileStats s) => Math.Max(0,
        s.Reports * PerReport + s.ConfirmationsReceived * PerConfirmationReceived + s.Votes * PerVote + s.Removed * PerRemovedReport);

    public static int LevelIndex(int points) => Array.FindLastIndex(Levels, l => points >= l.From);

    public static List<string> Badges(ProfileStats s)
    {
        var b = new List<string>();
        if (s.Reports >= 1) b.Add("FIRST_REPORT");
        if (s.Reports >= 10) b.Add("REPORTER_10");
        if (s.Reports >= 50) b.Add("REPORTER_50");
        if (s.ConfirmationsReceived >= 10) b.Add("HELPFUL_10");
        if (s.Votes >= 25) b.Add("CHECKER_25");
        return b;
    }

    private static readonly Regex Allowed = new(@"^[\p{L}\p{N}][\p{L}\p{N} ._-]{1,23}$");

    /// <summary>Trimmed nickname, or an error message.</summary>
    public static (string? Value, string? Error) CleanNickname(string? raw)
    {
        var s = raw?.Trim();
        if (string.IsNullOrEmpty(s)) return (null, null); // clearing is allowed
        if (!Allowed.IsMatch(s)) return (null, "2 to 24 letters, digits, spaces, dots, dashes or underscores.");
        return (s, null);
    }
}

public static class ProfileEndpoints
{
    public static RouteGroupBuilder MapProfileEndpoints(this RouteGroupBuilder v1)
    {
        var me = v1.MapGroup("/me").WithTags("Profile").RequireAuthorization();
        me.MapGet("", Get).WithName("GetProfile").RequireRateLimiting(RateLimits.Reads)
            .WithSummary("This device's points, level, badges and nickname.");
        me.MapPut("", Update).WithName("UpdateProfile").RequireRateLimiting(RateLimits.Votes)
            .WithSummary("Sets or clears the nickname (2-24 characters).");
        return v1;
    }

    private static async Task<Ok<ProfileDto>> Get(HttpContext http, RoadPulseDb db) =>
        TypedResults.Ok(await Load(db, http.User.DeviceId()));

    private static async Task<Results<Ok<ProfileDto>, ValidationProblem>> Update(UpdateProfileRequest request, HttpContext http, RoadPulseDb db)
    {
        var (nickname, error) = ProfileRules.CleanNickname(request.Nickname);
        if (error is not null) return TypedResults.ValidationProblem(new Dictionary<string, string[]> { ["nickname"] = [error] });
        var id = http.User.DeviceId();
        await db.Devices.Where(d => d.Id == id).ExecuteUpdateAsync(u => u.SetProperty(d => d.Nickname, nickname));
        return TypedResults.Ok(await Load(db, id));
    }

    internal static async Task<ProfileDto> Load(RoadPulseDb db, Guid deviceId)
    {
        var nickname = await db.Devices.Where(d => d.Id == deviceId).Select(d => d.Nickname).FirstOrDefaultAsync();
        var reports = await db.Reports.CountAsync(r => r.DeviceId == deviceId && r.Status != ReportStatus.Removed);
        var removed = await db.Reports.CountAsync(r => r.DeviceId == deviceId && r.Status == ReportStatus.Removed);
        var confirmations = await db.ReportVotes.CountAsync(v => v.StillThere && v.DeviceId != deviceId &&
            db.Reports.Any(r => r.Id == v.ReportId && r.DeviceId == deviceId && r.Status != ReportStatus.Removed));
        var votes = await db.ReportVotes.CountAsync(v => v.DeviceId == deviceId);
        var stats = new ProfileStats(reports, confirmations, votes, removed);
        var points = ProfileRules.Points(stats);
        var level = ProfileRules.LevelIndex(points);
        var next = level + 1 < ProfileRules.Levels.Length ? ProfileRules.Levels[level + 1].From : (int?)null;
        return new ProfileDto(nickname, points, ProfileRules.Levels[level].Name, level, next, stats, ProfileRules.Badges(stats));
    }
}
