# 0001. Backend framework: ASP.NET Core on .NET 10 (LTS)

Date: 2026-11-14 · Status: Accepted

## Context

Phase 2 needs a backend for community reports, votes, expiry, moderation, accounts, and live
updates to the app. The plan proposed Ktor or Spring Boot and allowed ASP.NET Core only if
explicitly selected after review. The project owner explicitly selected ASP.NET Core because the
team works in C#/.NET.

Requirements from plan section C: OpenAPI contracts, authentication, input validation, pagination,
idempotent writes, rate limits, consistent error schema, versioning, PostgreSQL/PostGIS, realtime
updates, structured logs, metrics and traces.

## Decision

- **ASP.NET Core on .NET 10**, the current long-term-support release (supported until
  14 November 2028). Move to the next LTS (.NET 12, expected November 2027) during 2028.
- **Modular monolith**: one deployable API with internal modules (Reports, Moderation, Accounts,
  Telemetry later) behind clear interfaces. Valhalla (routing), Photon (search) and map tiles stay
  separate processes. Split services only when there is a measured need.
- **Data**: PostgreSQL 16 + PostGIS through EF Core with Npgsql and NetTopologySuite
  (`geography(Point)` columns, spatial indexes). Schema changes only through EF Core migrations.
- **API**: minimal APIs grouped per module, versioned under `/v1`, OpenAPI document generated at
  build time and checked into the repository. Errors use RFC 9457 problem details. Writes accept an
  `Idempotency-Key` header. Built-in rate limiting middleware per device/user and per IP.
- **Realtime**: SignalR for live report updates, with clients subscribed by map tile; add a Redis
  backplane only when a second API instance is needed.
- **Observability**: OpenTelemetry for traces and metrics, structured JSON logs; never log precise
  locations, tokens or identifiable trip data (plan section E).
- **Tests**: xUnit; integration tests run against real PostgreSQL/PostGIS in Testcontainers.

## Consequences

- Two languages: Kotlin (app) and C# (backend). The OpenAPI document is the contract between them;
  the app's API client is generated from it (or written against it) and contract tests run in CI.
- Strong tooling, performance and PostGIS support; hosting on Linux containers (see 0002).
- The existing `backend/db/schema.sql` becomes the starting point for the first EF Core migration.
- CI gains a second job (`dotnet build`, `dotnet test`) next to the Android job.
