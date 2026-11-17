using System;
using Microsoft.EntityFrameworkCore.Migrations;
using NetTopologySuite.Geometries;

#nullable disable

namespace RoadPulse.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class PlaceProblems : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "place_corrections",
                columns: table => new
                {
                    place_id = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    hidden = table.Column<bool>(type: "boolean", nullable: false),
                    location = table.Column<Point>(type: "geography (point, 4326)", nullable: true),
                    updated_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_place_corrections", x => x.place_id);
                });

            migrationBuilder.CreateTable(
                name: "place_problems",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false),
                    device_id = table.Column<Guid>(type: "uuid", nullable: false),
                    place_id = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false),
                    place_name = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: false),
                    kind = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    suggested = table.Column<Point>(type: "geography (point, 4326)", nullable: true),
                    note = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: true),
                    created_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false),
                    status = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    reviewed_at = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_place_problems", x => x.id);
                    table.ForeignKey(
                        name: "fk_place_problems_devices_device_id",
                        column: x => x.device_id,
                        principalTable: "devices",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "ix_place_problems_device_id",
                table: "place_problems",
                column: "device_id");

            migrationBuilder.CreateIndex(
                name: "ix_place_problems_status",
                table: "place_problems",
                column: "status");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "place_corrections");

            migrationBuilder.DropTable(
                name: "place_problems");
        }
    }
}
