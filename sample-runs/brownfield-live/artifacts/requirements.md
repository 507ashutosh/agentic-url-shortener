# requirements

**Summary:** Turned "links should expire after N days" into a scoped requirement with testable acceptance criteria, recorded assumptions and open questions.

## Decisions and assumptions

- **DECISION:** N is a single global, configurable value in this iteration, not set per link — because The requirement names one "N" and does not mention per-link control. Per-link expiry adds API surface and is listed as a question.
- **DECISION:** Expiry is measured from the link's creation timestamp — because "Expire after N days" most plainly means age since creation. Sliding expiry based on last access is a different feature.
- **DECISION:** Expired links stop resolving but are not physically deleted as part of this requirement — because Deletion and code reuse are irreversible and were not requested. Keeping the record is the safer default.
- **ASSUMPTION:** N is set through Spring configuration, e.g. `shortener.link.expiry-days`, and applies to all links.
- **ASSUMPTION:** The default N is 30 days. This is a placeholder until a human confirms it.
- **ASSUMPTION:** A link expires at exactly `createdAt + N × 24h`, computed in UTC from an injectable `java.time.Clock` so tests can control time. Calendar-day and timezone semantics are not used.
- **ASSUMPTION:** Resolving an expired link returns HTTP 410 Gone with no redirect. A link that never existed keeps its current response, presumably 404.
- **ASSUMPTION:** Expiry is computed at resolve time from `createdAt` and the current N. Changing N therefore affects existing links too.
- **ASSUMPTION:** Existing links that are already older than N days become expired as soon as the feature is deployed.
- **ASSUMPTION:** The short code of an expired link is not reissued to a new link.
- **ASSUMPTION:** Link creation and lookup API responses expose an `expiresAt` field. This is a minor additive change.
- **QUESTION:** What should N be by default, and must it be per-environment configurable only, or should API callers be able to set a per-link expiry?
- **QUESTION:** Should expiry count from creation (fixed) or from last access (sliding)?
- **QUESTION:** Should expiry apply retroactively to existing links? This could break many live links at deploy time, which is high impact. Should there be a grace period or migration instead?
- **QUESTION:** What should an expired link return: 410 Gone, 404 Not Found, or a redirect to an "expired" page?
- **QUESTION:** Should expired links eventually be purged from storage, and may their short codes be reused?
- **QUESTION:** Should some links be able to never expire (e.g., N = 0 or a per-link flag)?
- **QUESTION:** Is "days" meant as 24-hour periods (assumed) or calendar days in some timezone?

## Report

# Requirement Analysis: Link Expiry

## Intent
Short links should stop working once they are older than a configured number of days (N). This limits how long stale links stay live.

## Scope

**In scope**
- A global, configurable expiry period N, measured in days.
- Expiry evaluated when a short link is resolved, based on the link's `createdAt` timestamp.
- A defined HTTP response when an expired link is resolved.
- Exposing each link's computed `expiresAt` in API responses that return link details.
- Time handling through an injectable `Clock` so the behaviour is testable.

**Out of scope (unless confirmed via the questions above)**
- Per-link or caller-specified expiry.
- Sliding expiry based on last access.
- Physical deletion or purge jobs for expired links.
- Reusing short codes from expired links.
- "Never expire" links.
- Notifications, analytics or UI for expiry.

## Acceptance Criteria

1. **Configuration**
   - The expiry period is read from configuration property `shortener.link.expiry-days`, an integer of 1 or more.
   - If the property is not set, it defaults to 30.
2. **Validation**
   - The application fails to start if `shortener.link.expiry-days` is configured as 0, negative or non-numeric. The failure message names the property.
3. **Active link resolves**
   - Given a link created at time T and N = 7, resolving it at T + 7d − 1s redirects to the target URL, exactly as it does today.
4. **Expired link does not resolve**
   - Given the same link, resolving it at exactly T + 7d returns HTTP 410 Gone.
   - Resolving it at any later time also returns HTTP 410 Gone.
   - In both cases no `Location` header is sent.
5. **Unknown codes unchanged**
   - Resolving a short code that never existed returns the same status as before this change.
   - That status is different from the expired-link response.
6. **`expiresAt` exposed**
   - Link creation and link lookup responses include `expiresAt`, equal to `createdAt + N days`.
   - The value is an ISO-8601 UTC timestamp.
   - All existing response fields are unchanged.
7. **Existing links**
   - A link persisted before this feature, with `createdAt` older than N days, returns 410 when resolved.
   - A link persisted before this feature, with `createdAt` within N days, still redirects.
8. **No data loss**
   - Expired links remain in storage after expiry.
   - Their short codes are never assigned to newly created links.
9. **Config change behaviour**
   - Changing N and restarting the application changes the expiry of all links, both existing and new, according to the new value.
10. **Testability**
    - All expiry decisions use an injected `java.time.Clock`.
    - Automated tests cover criteria 3, 4, 5, 6 and 7 using a fixed or offset clock, without real waiting.

## Risks / Notes for Downstream Agents
- **Retroactive breakage:** Criterion 7 may break many live links at deploy time. This is high impact, so a human should approve it before release (see the questions above).
- **Schema:** If links do not currently store `createdAt`, a schema migration is needed. It must also decide how to backfill `createdAt` for existing rows, which is another point for human review.
- **Caching:** Any redirect caching, whether HTTP cache headers or an in-app cache, must not keep serving a link past its `expiresAt`.
