# plan

**Summary:** Broke the link-expiry requirement into 16 dependency-ordered tasks across design, implement, tests, docs, validate and release, and marked which can run in parallel.

## Decisions and assumptions

- **DECISION:** Start with a discovery task (T1) before any implementation. — because The analysis does not say whether `createdAt` is stored, whether redirect caching exists, or what unknown codes currently return. All three decide whether T8, T9 and parts of T12 are needed. _(based on: requirements)_
- **DECISION:** Make the migration (T8) and cache fix (T9) conditional tasks. — because They are only needed if T1 finds that `createdAt` is missing or that redirects are cached. Planning them as conditional avoids inventing work. _(based on: requirements)_
- **DECISION:** Put a human approval gate (T16) before release. — because Retroactive expiry (criterion 7) and any `createdAt` backfill can break live links at deploy time. The requirement analysis flags both as high impact. _(based on: requirements)_
- **DECISION:** Use a typed, validated configuration-properties class for `shortener.link.expiry-days`. — because Spring Boot's binding and validation fail startup with a message naming the property, which meets criterion 2 without custom code. _(based on: requirements)_
- **DECISION:** Define "expired" as `now >= createdAt + N days`. — because Criteria 3 and 4 put the boundary exactly at T + N days. _(based on: requirements)_
- **ASSUMPTION:** `expiresAt` is computed from `createdAt` and N each time, never stored. This follows from criterion 9: changing N and restarting changes expiry for all links. _(based on: requirements)_
- **ASSUMPTION:** Short codes are never reused today, because generation checks for existing codes and nothing deletes them. Criterion 8 therefore needs only test coverage, not new logic. T1 confirms this. _(based on: requirements)_
- **ASSUMPTION:** The expiry check runs in the service layer, not in a scheduled job. Purge jobs are out of scope. _(based on: requirements)_
- **QUESTION:** Has a human approved retroactive expiry of links created before this feature (criterion 7)? Or should existing links get a grace period? _(based on: requirements)_
- **QUESTION:** If `createdAt` is missing for existing rows, what backfill value is approved? Options include migration time or a known historical date. _(based on: requirements)_
- **QUESTION:** What status do unknown codes return today (for example 404)? Tests in T12 need this to pin the "unchanged" behaviour. _(based on: requirements)_

## Report

# Plan: Link Expiry After N Days

| ID | Task | Stage | Depends on |
|----|------|-------|------------|
| T1 | Inspect the current code: link entity and schema (is `createdAt` stored?), resolve, create and lookup endpoints, unknown-code status, any redirect caching (HTTP headers or in-app), and how short codes are generated | design | – |
| T2 | Design note: expiry rule (`now >= createdAt + N days`), 410 response with no `Location`, where `expiresAt` appears in responses, and migration/backfill plan if needed (needs human review) | design | T1 |
| T3 | Add a `@ConfigurationProperties("shortener.link")` class with validated `expiryDays` (minimum 1, default 30) | implement | T2 |
| T4 | Register a `java.time.Clock` bean (`Clock.systemUTC()`) and inject it where expiry is evaluated | implement | T2 |
| T5 | Add domain/service logic: `expiresAt(link)` and `isExpired(link, clock)` using N from T3 | implement | T3, T4 |
| T6 | Resolve endpoint: return 410 Gone with no `Location` header for expired links; keep redirect and unknown-code behaviour unchanged | implement | T5 |
| T7 | Add `expiresAt` (ISO-8601 UTC) to create and lookup response DTOs; leave existing fields untouched | implement | T5 |
| T8 | (Only if T1 finds `createdAt` missing) Add a DB migration for `createdAt` with the approved backfill | implement | T2 |
| T9 | (Only if T1 finds redirect caching) Make sure no cache serves a link past its `expiresAt`, by bounding TTL or headers | implement | T5, T6 |
| T10 | Config tests: default is 30; startup fails for 0, negative and non-numeric values, with the failure message naming `shortener.link.expiry-days` | tests | T3 |
| T11 | Unit tests with a fixed clock and N=7: active at T+7d−1s, expired at T+7d and later | tests | T5 |
| T12 | Integration tests with a fixed or offset clock: redirect vs 410 with no `Location`, unknown-code status unchanged and different from 410, `expiresAt` in create and lookup, existing rows old vs recent, expired row still stored and its code not reused, changed N applies to existing links | tests | T6, T7, T8*, T9* |
| T13 | Document the `shortener.link.expiry-days` property, 410 behaviour, `expiresAt` field and retroactive effect (README/API docs/changelog) | docs | T2 |
| T14 | Run the full build and test suite; check each acceptance criterion (1–10) against the evidence | validate | T10, T11, T12, T13 |
| T15 | Prepare release notes covering the retroactive-expiry impact and migration steps | release | T13, T14 |
| T16 | Human approval gate for retroactive expiry and any backfill, then release | release | T15 |

\* T8 and T9 are dependencies only if they turn out to be needed (see T1).

## Parallelism
- **After T2:** T3, T4, T8 and T13 can run in parallel.
- **After T5:** T6, T7 and T11 can run in parallel. T10 can start as soon as T3 is done.
- **After T6:** T9 can run alongside T7 and the remaining test work.
- **Strictly sequential tail:** T12 → T14 → T15 → T16.
