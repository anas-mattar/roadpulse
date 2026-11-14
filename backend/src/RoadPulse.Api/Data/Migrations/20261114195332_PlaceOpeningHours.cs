using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace RoadPulse.Api.Data.Migrations
{
    /// <inheritdoc />
    public partial class PlaceOpeningHours : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "opening_hours",
                table: "places",
                type: "character varying(255)",
                maxLength: 255,
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "opening_hours",
                table: "places");
        }
    }
}
