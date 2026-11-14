using System.Text.Json.Serialization;
using Microsoft.EntityFrameworkCore;
using RoadPulse.Api;
using RoadPulse.Api.Data;
using RoadPulse.Api.Modules.Devices;
using RoadPulse.Api.Modules.Live;
using RoadPulse.Api.Modules.Moderation;
using RoadPulse.Api.Modules.Reports;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddDbContext<RoadPulseDb>(o =>
    o.UseNpgsql(builder.Configuration.GetConnectionString("Default"), npgsql => npgsql.UseNetTopologySuite())
     .UseSnakeCaseNamingConvention());
builder.Services.AddSingleton(TimeProvider.System);
builder.Services.ConfigureHttpJsonOptions(o => o.SerializerOptions.Converters.Add(new JsonStringEnumConverter()));
// Malformed JSON or unknown enum values: a 400 problem response that says what is wrong.
builder.Services.Configure<Microsoft.AspNetCore.Routing.RouteHandlerOptions>(o => o.ThrowOnBadRequest = true);
builder.Services.AddProblemDetails(o => o.CustomizeProblemDetails = ctx =>
{
    if (ctx.HttpContext.Features.Get<Microsoft.AspNetCore.Diagnostics.IExceptionHandlerFeature>()?.Error is BadHttpRequestException)
    {
        ctx.ProblemDetails.Status = StatusCodes.Status400BadRequest;
        ctx.HttpContext.Response.StatusCode = StatusCodes.Status400BadRequest;
        ctx.ProblemDetails.Title = "Invalid request";
        ctx.ProblemDetails.Detail = "The request body could not be read. Check the JSON, field names and values " +
            "(type must be one of: " + string.Join(", ", Enum.GetNames<ReportType>()) + ").";
    }
});
builder.Services.AddSignalR(o => o.MaximumReceiveMessageSize = 4 * 1024)
    .AddJsonProtocol(o => o.PayloadSerializerOptions.Converters.Add(new JsonStringEnumConverter()));
builder.Services.AddSingleton<LiveReports>();
builder.Services.AddOpenApi("v1");
builder.Services.AddAuthentication(DeviceAuthHandler.SchemeName)
    .AddScheme<Microsoft.AspNetCore.Authentication.AuthenticationSchemeOptions, DeviceAuthHandler>(DeviceAuthHandler.SchemeName, null)
    .AddScheme<Microsoft.AspNetCore.Authentication.AuthenticationSchemeOptions, ModeratorAuthHandler>(ModeratorAuthHandler.SchemeName, null);
builder.Services.Configure<ModerationOptions>(builder.Configuration.GetSection("Moderation"));
builder.Services.AddAuthorizationBuilder()
    .AddPolicy(ModeratorAuthHandler.Policy, p => p.AddAuthenticationSchemes(ModeratorAuthHandler.SchemeName).RequireRole("moderator"));
builder.Services.AddRoadPulseRateLimits(builder.Configuration);

var app = builder.Build();

if (app.Configuration.GetValue("Database:MigrateOnStartup", false))
{
    using var scope = app.Services.CreateScope();
    scope.ServiceProvider.GetRequiredService<RoadPulseDb>().Database.Migrate();
}

app.UseExceptionHandler();

// Moderation web page (wwwroot/admin): static files, strict browser security headers, no caching.
// It shows nothing by itself; every action calls the moderation API with the moderator's key.
if (app.Configuration.GetValue("Moderation:WebPage", true))
{
    app.Use(async (ctx, next) =>
    {
        if (ctx.Request.Path.StartsWithSegments("/admin"))
        {
            if (ctx.Request.Path.Value == "/admin") { ctx.Response.Redirect("/admin/"); return; }
            var h = ctx.Response.Headers;
            h.ContentSecurityPolicy = "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";
            h.XContentTypeOptions = "nosniff";
            h["Referrer-Policy"] = "no-referrer";
            h.CacheControl = "no-store";
        }
        await next();
    });
    app.UseDefaultFiles();
    app.UseStaticFiles();
}
app.UseStatusCodePages();
app.UseAuthentication();
// Before authorization, so rejected attempts (such as guessed moderator keys) also count.
app.UseRateLimiter();
app.UseAuthorization();

app.MapOpenApi("/openapi/{documentName}.json");
app.MapGet("/health", async (RoadPulseDb db) =>
    await db.Database.CanConnectAsync() ? Results.Ok(new { status = "ok" }) : Results.Problem("Database unavailable", statusCode: 503));

var v1 = app.MapGroup("/v1");
v1.MapDeviceEndpoints();
v1.MapReportEndpoints();
v1.MapModerationEndpoints();
app.MapHub<ReportsHub>(ReportsHub.Path).RequireRateLimiting(RateLimits.Reads);

app.Run();

/// <summary>Entry point, public so integration tests can host the API.</summary>
public partial class Program;
