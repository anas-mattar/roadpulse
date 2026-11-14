using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Reports;

namespace RoadPulse.Api.Tests;

public class ReportRulesTests
{
    private static readonly DateTimeOffset T0 = new(2026, 11, 14, 12, 0, 0, TimeSpan.Zero);

    [Fact]
    public void ConfirmationExtendsButNeverBeyondMaxLifetime()
    {
        var ttl = ReportRules.TimeToLive(ReportType.POLICE); // 45 min
        var expires = T0 + ttl;
        // Confirmed after 40 min: now + 22.5 min is later than the original expiry.
        Assert.Equal(T0.AddMinutes(62.5), ReportRules.ExtendOnConfirm(ReportType.POLICE, T0, expires, T0.AddMinutes(40)));
        // Confirmed early: original expiry is kept.
        Assert.Equal(expires, ReportRules.ExtendOnConfirm(ReportType.POLICE, T0, expires, T0.AddMinutes(5)));
        // Confirmed very late: capped at 4x TTL from creation.
        Assert.Equal(T0 + ttl * 4, ReportRules.ExtendOnConfirm(ReportType.POLICE, T0, T0.AddHours(2.9), T0.AddHours(2.95)));
    }

    [Theory]
    [InlineData(0, 1, false)]
    [InlineData(0, 2, true)]
    [InlineData(2, 2, false)]
    [InlineData(1, 3, true)]
    public void RemovalNeedsTwoDismissalsOutnumberingConfirmations(int confirmations, int dismissals, bool removed) =>
        Assert.Equal(removed, ReportRules.ShouldRemove(confirmations, dismissals));

    [Fact]
    public void EveryTypeHasATimeToLive()
    {
        foreach (var type in Enum.GetValues<ReportType>())
            Assert.InRange(ReportRules.TimeToLive(type), TimeSpan.FromMinutes(10), TimeSpan.FromHours(12));
    }
}

public class ModerationRulesTests
{
    [Theory]
    [InlineData(2, false, 3, false)]
    [InlineData(3, false, 3, true)]
    [InlineData(10, true, 3, false)] // restored by a moderator: stays up
    public void HideForReview(int flags, bool reviewed, int threshold, bool expected) =>
        Assert.Equal(expected, RoadPulse.Api.Modules.Reports.ReportRules.ShouldHideForReview(flags, reviewed, threshold));
}
