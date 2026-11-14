# Architecture decision records

Short records of significant decisions: context, decision, consequences. A new decision that
changes an earlier one gets a new record that supersedes it; old records are not rewritten.

| # | Decision | Status |
|---|---|---|
| [0001](0001-backend-framework.md) | Backend framework: ASP.NET Core on .NET 10 (LTS), modular monolith | Accepted |
| [0002](0002-hosting.md) | Hosting: Docker Compose on Linux VPS in Singapore | Accepted (deployment deferred; local only) |
| [0003](0003-offline-scope.md) | Offline scope for the first public release | Accepted |
| [0004](0004-location-telemetry.md) | Location telemetry for live traffic: opt-in, aggregated, short retention | Proposed (needs legal review) |
| [0005](0005-map-data-pipeline.md) | Map data pipeline: weekly OpenStreetMap refresh for routing, search and tiles | Accepted |
| [0006](0006-identity.md) | User identity for the community beta | Accepted |

Status values: Proposed, Accepted, Superseded by NNNN.
