# 0002. Hosting: Docker Compose on Linux VPS in Singapore

Date: 2026-11-14 · Status: Accepted

## Context

The beta covers one metro area (Klang Valley). The team is small. The app currently depends on free
public servers (OpenFreeMap tiles, the FOSSGIS Valhalla demo, Komoot's Photon) whose usage
policies do not allow production traffic. Plan section C asks for reproducible Docker-based
development, simple production hosting, and Kubernetes only when load justifies it.

## Decision

- **Linux VPS in a Singapore data centre** (closest major region to Malaysia; pick any provider with
  Singapore servers, snapshots and private networking). Revisit a Malaysian region if latency or
  data-residency advice requires it.
- **Two servers for the beta**, each running Docker Compose:
  - *app*: ASP.NET Core API, PostgreSQL/PostGIS, Caddy (TLS and reverse proxy).
  - *maps*: Valhalla (Malaysia/Singapore/Brunei extract), Photon, and the vector tile files
    (PMTiles) served by Caddy. Map tiles can later move to object storage with a CDN in front.
- **Staging** is a third, smaller server with the same Compose files.
- **Deploys**: GitHub Actions builds container images, pushes them to GitHub Container Registry,
  and deploys over SSH (`docker compose pull && docker compose up -d`). Every image is tagged with
  the commit; rollback = redeploy the previous tag.
- **Backups**: nightly encrypted `pg_dump` to storage in a different provider or region; a restore
  is tested monthly on staging. VPS snapshots are an extra, not the backup.
- **Before public beta**: the app switches from the public demo servers to our own (the URLs are
  already settings in `gradle.properties`).

## Consequences

- Low cost and few moving parts; one person can run it with the documented steps.
- No automatic failover: a server outage means downtime until restored from snapshot or backup.
  Acceptable for a beta; revisit before wide public release (Gate 4).
- Kubernetes, managed databases and multi-region are deferred, not rejected.
