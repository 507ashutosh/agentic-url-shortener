# docs

**Summary:** Created `docs/features/link-expiry.md`, the user-facing guide to global N-day link expiry. It covers behaviour, API changes with curl examples, error cases, the `shortener.link.expiry-days` setting and upgrade notes.

## Decisions and assumptions

- **DECISION:** Put all the content the design asked for in the README section and CHANGELOG entry into one file, `docs/features/link-expiry.md`. — because I may only write under `docs/`, and the task asks for one Markdown file under `docs/features/`. `README.md` and `CHANGELOG.md` sit outside that path. _(based on: requirements, design)_
- **DECISION:** Left out bodies for 404, 400 and 429 responses and said only that they are unchanged. — because Neither the design nor the requirements give those bodies, and writing them out would invent a contract. _(based on: requirements, design)_
- **DECISION:** Described the expiry window as exactly N×24 hours in UTC, not N calendar days. — because The design uses `Duration.ofDays(N)` on an `Instant`, and the acceptance criteria say `createdAt` plus N×24h. _(based on: requirements, design)_
- **DECISION:** Marked the retroactive expiry of existing links as "pending human approval" in the upgrade notes. — because Both upstream stages flag it as a release gate. The doc should not read as if it were already approved. _(based on: requirements, design)_
- **ASSUMPTION:** Examples use the base URL `http://localhost:8080`, matching the `shortUrl` example in the design. _(based on: requirements, design)_
- **ASSUMPTION:** Existing rows already have a usable `createdAt`, because the design says no schema change or backfill is needed. _(based on: requirements, design)_
- **ASSUMPTION:** The default UTC timestamp format (no fractional seconds when they are zero) applies to `createdAt` and `lastAccessedAt` as well as `expiresAt`. The doc only states the format rule for `expiresAt`. _(based on: requirements, design)_
- **QUESTION:** Should someone with access outside `docs/` add the README section and CHANGELOG entry the design asks for, perhaps by linking to this file? Or is this feature doc enough? _(based on: requirements, design)_
- **QUESTION:** Has the retroactive expiry been approved? If so, should the doc give a specific value of N or an approved migration approach (for example, temporarily raising N) for the first deploy? _(based on: requirements, design)_
- **QUESTION:** Is any redirect caching in place, such as a CDN, a reverse proxy or `Cache-Control` on the 302? The requirements say caches must not serve a link past `expiresAt`, but the design does not cover this, so the doc does not mention it. _(based on: requirements, design)_

## Files written

- `docs/features/link-expiry.md`

## Report

# Report

I wrote one feature document, `docs/features/link-expiry.md`. It covers:

- **Behaviour:** links expire once `now >= createdAt + N×24h`. Expired links then return 410 Gone with no `Location` header, and those attempts are not counted as clicks.
- **API changes:** the new `expiresAt` field on `POST /shorten` and `GET /stats/{code}`, with curl examples for each.
- **Errors:** expired (410), unknown (404, unchanged) and the cases where startup fails because of bad configuration.
- **Configuration:** the `shortener.link.expiry-days` property (minimum 1, default 30).
- **Upgrade notes:**
  - existing old links stop working retroactively at deploy
  - restarting with a different N re-evaluates every link, so raising N revives expired links
  - expired links stay in storage and their codes are not reused
  - strict JSON clients need updating for the new field

I did not edit the code-level docs (OpenAPI annotations, `OpenApiConfig`, the `application.properties` comment), because the implement agent owns them. I also did not edit README or CHANGELOG, because they are outside the paths I may write to.
