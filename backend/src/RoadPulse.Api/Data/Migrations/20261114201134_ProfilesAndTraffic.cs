using System;
using Microsoft.EntityFrameworkCore.Migrations;
using NetTopologySuite.Geometries;

#nullable disable

namespace RoadPulse.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class ProfilesAndTraffic : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "nickname",
                table: "devices",
                type: "character varying(24)",
                maxLength: 24,
                nullable: true);

            migrationBuilder.CreateTable(
                name: "traffic_edges",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false),
                    way_id = table.Column<long>(type: "bigint", nullable: false),
                    expected_kph = table.Column<int>(type: "integer", nullable: false),
                    geometry = table.Column<LineString>(type: "geography (linestring, 4326)", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_traffic_edges", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "traffic_observations",
                columns: table => new
                {
                    edge_id = table.Column<long>(type: "bigint", nullable: false),
                    window_start = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    speed_sum_kph = table.Column<double>(type: "double precision", nullable: false),
                    segments = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_traffic_observations", x => new { x.edge_id, x.window_start });
                });

            migrationBuilder.CreateIndex(
                name: "ix_traffic_edges_geometry",
                table: "traffic_edges",
                column: "geometry")
                .Annotation("Npgsql:IndexMethod", "gist");

            migrationBuilder.CreateIndex(
                name: "ix_traffic_observations_window_start",
                table: "traffic_observations",
                column: "window_start");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "traffic_edges");

            migrationBuilder.DropTable(
                name: "traffic_observations");

            migrationBuilder.DropColumn(
                name: "nickname",
                table: "devices");
        }
    }
}
