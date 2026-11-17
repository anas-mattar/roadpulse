using Microsoft.EntityFrameworkCore;

namespace RoadPulse.Api.Data;

public class RoadPulseDb(DbContextOptions<RoadPulseDb> options) : DbContext(options)
{
    public DbSet<Device> Devices => Set<Device>();
    public DbSet<Report> Reports => Set<Report>();
    public DbSet<ReportVote> ReportVotes => Set<ReportVote>();
    public DbSet<IdempotencyRecord> IdempotencyRecords => Set<IdempotencyRecord>();
    public DbSet<ReportFlag> ReportFlags => Set<ReportFlag>();
    public DbSet<AuditEntry> AuditLog => Set<AuditEntry>();
    public DbSet<MapPlace> Places => Set<MapPlace>();
    public DbSet<TrafficEdge> TrafficEdges => Set<TrafficEdge>();
    public DbSet<TrafficObservation> TrafficObservations => Set<TrafficObservation>();
    public DbSet<CommunityPlace> CommunityPlaces => Set<CommunityPlace>();
    public DbSet<PlaceProblem> PlaceProblems => Set<PlaceProblem>();
    public DbSet<PlaceCorrection> PlaceCorrections => Set<PlaceCorrection>();
    public DbSet<DirectoryPlace> DirectoryPlaces => Set<DirectoryPlace>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.HasPostgresExtension("postgis");
        b.HasPostgresExtension("pg_trgm");

        b.Entity<Device>(e =>
        {
            e.ToTable("devices");
            e.HasIndex(d => d.TokenHash).IsUnique();
            e.Property(d => d.TokenHash).HasMaxLength(64);
            e.Property(d => d.Nickname).HasMaxLength(24);
        });

        b.Entity<Report>(e =>
        {
            e.ToTable("reports");
            e.Property(r => r.Type).HasConversion<string>().HasMaxLength(20);
            e.Property(r => r.Location).HasColumnType("geography (point, 4326)");
            e.HasIndex(r => r.Location).HasMethod("gist");
            e.HasIndex(r => r.ExpiresAt);
            e.HasIndex(r => r.DeviceId);
            e.Property(r => r.Status).HasConversion<string>().HasMaxLength(20);
            e.Property(r => r.Subtype).HasMaxLength(30);
            e.HasOne<Device>().WithMany().HasForeignKey(r => r.DeviceId);
        });

        b.Entity<ReportVote>(e =>
        {
            e.ToTable("report_votes");
            e.HasKey(v => new { v.ReportId, v.DeviceId });
            e.HasOne<Report>().WithMany(r => r.Votes).HasForeignKey(v => v.ReportId).OnDelete(DeleteBehavior.Cascade);
            e.HasOne<Device>().WithMany().HasForeignKey(v => v.DeviceId);
        });

        b.Entity<ReportFlag>(e =>
        {
            e.ToTable("report_flags");
            e.HasKey(f => new { f.ReportId, f.DeviceId });
            e.Property(f => f.Reason).HasConversion<string>().HasMaxLength(20);
            e.HasOne<Report>().WithMany(r => r.FlagEntries).HasForeignKey(f => f.ReportId).OnDelete(DeleteBehavior.Cascade);
            e.HasOne<Device>().WithMany().HasForeignKey(f => f.DeviceId);
        });

        b.Entity<AuditEntry>(e =>
        {
            e.ToTable("audit_log");
            e.Property(a => a.Moderator).HasMaxLength(50);
            e.Property(a => a.Action).HasMaxLength(30);
            e.Property(a => a.TargetType).HasMaxLength(20);
            e.Property(a => a.Reason).HasMaxLength(500);
            e.HasIndex(a => a.At);
            e.HasIndex(a => a.TargetId);
        });

        b.Entity<MapPlace>(e =>
        {
            e.ToTable("places");
            e.Property(p => p.Id).HasMaxLength(24);
            e.Property(p => p.Category).HasConversion<string>().HasMaxLength(20);
            e.Property(p => p.Name).HasMaxLength(200);
            e.Property(p => p.Brand).HasMaxLength(100);
            e.Property(p => p.OpeningHours).HasMaxLength(255);
            e.Property(p => p.Location).HasColumnType("geography (point, 4326)");
            e.HasIndex(p => p.Location).HasMethod("gist");
            e.HasIndex(p => p.Category);
        });

        b.Entity<DirectoryPlace>(e =>
        {
            e.ToTable("directory_places");
            e.Property(p => p.Id).HasMaxLength(64);
            e.Property(p => p.Name).HasMaxLength(200);
            e.Property(p => p.Category).HasMaxLength(60);
            e.Property(p => p.Address).HasMaxLength(200);
            e.Property(p => p.SearchKey).HasMaxLength(200);
            e.Property(p => p.Location).HasColumnType("geography (point, 4326)");
            e.HasIndex(p => p.Location).HasMethod("gist");
            // "contains" search on names is fast with a trigram index.
            e.HasIndex(p => p.SearchKey).HasMethod("gin").HasOperators("gin_trgm_ops");
        });

        b.Entity<PlaceProblem>(e =>
        {
            e.ToTable("place_problems");
            e.Property(p => p.PlaceId).HasMaxLength(64);
            e.Property(p => p.PlaceName).HasMaxLength(200);
            e.Property(p => p.Note).HasMaxLength(200);
            e.Property(p => p.Kind).HasConversion<string>().HasMaxLength(20);
            e.Property(p => p.Status).HasConversion<string>().HasMaxLength(20);
            e.Property(p => p.Suggested).HasColumnType("geography (point, 4326)");
            e.HasIndex(p => p.Status);
            e.HasIndex(p => p.DeviceId);
            e.HasOne<Device>().WithMany().HasForeignKey(p => p.DeviceId);
        });

        b.Entity<PlaceCorrection>(e =>
        {
            e.ToTable("place_corrections");
            e.HasKey(c => c.PlaceId);
            e.Property(c => c.PlaceId).HasMaxLength(64);
            e.Property(c => c.Location).HasColumnType("geography (point, 4326)");
        });

        b.Entity<CommunityPlace>(e =>
        {
            e.ToTable("community_places");
            e.Property(p => p.Name).HasMaxLength(80);
            e.Property(p => p.Note).HasMaxLength(120);
            e.Property(p => p.SearchKey).HasMaxLength(80);
            e.Property(p => p.Kind).HasConversion<string>().HasMaxLength(20);
            e.Property(p => p.Status).HasConversion<string>().HasMaxLength(20);
            e.Property(p => p.Location).HasColumnType("geography (point, 4326)");
            e.HasIndex(p => p.Location).HasMethod("gist");
            e.HasIndex(p => p.Status);
            e.HasIndex(p => p.DeviceId);
            e.HasOne<Device>().WithMany().HasForeignKey(p => p.DeviceId);
        });

        b.Entity<TrafficEdge>(e =>
        {
            e.ToTable("traffic_edges");
            e.Property(t => t.Id).ValueGeneratedNever();
            e.Property(t => t.Geometry).HasColumnType("geography (linestring, 4326)");
            e.HasIndex(t => t.Geometry).HasMethod("gist");
        });

        b.Entity<TrafficObservation>(e =>
        {
            e.ToTable("traffic_observations");
            e.HasKey(o => new { o.EdgeId, o.WindowStart });
            e.HasIndex(o => o.WindowStart);
        });

        b.Entity<IdempotencyRecord>(e =>
        {
            e.ToTable("idempotency_records");
            e.HasKey(i => new { i.DeviceId, i.Key });
            e.Property(i => i.Key).HasMaxLength(100);
            e.HasIndex(i => i.CreatedAt);
        });
    }
}
