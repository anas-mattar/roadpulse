# 0006. User identity for the community beta

Date: 2026-11-14 · Status: Proposed (needs your decision)

## Context

Gate 2 (community beta) needs "minimal sign-in or appropriate anonymous identity design" so reports
can be rate-limited, voted on once per person, and moderated. Full accounts, friends and ranks are
deferred (plan section F).

## Options

1. **Anonymous device identity** — the app creates a random key pair on first launch and signs its
   requests; the server knows "device X", nothing else. Simplest, no personal data; but a person can
   reset it by reinstalling, so abuse controls rely more on rate limits and IP signals.
2. **Phone-number sign-in** (one-time SMS code) — strong protection against spam accounts and
   familiar in Malaysia; costs per SMS and stores personal data (PDPA).
3. **Google sign-in** (via Firebase Auth or an OpenID Connect provider such as Keycloak) — easy for
   Android users, no SMS cost; stores an email address.

## Recommendation

Start the beta with **option 1 (anonymous device identity)** for reading and reporting, with tight
rate limits and moderation. Add **option 3 (Google sign-in)** as optional when accounts, points and
friends arrive (Phase 4), and require it for heavy reporters if spam appears. The ASP.NET Core
backend (0001) supports both through its standard authentication middleware.

## Decision

Pending: confirm the recommendation or choose another option.
