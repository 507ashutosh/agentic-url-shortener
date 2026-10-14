# impact-analysis

**Summary:** Impact analysis for global N-day link expiry. No schema change is needed because `created_at` already exists and is NOT NULL. The changes are the config properties, `UrlService.resolve`, the exception handler, two response DTOs, the controller's OpenAPI docs and the tests.

## Decisions and assumptions

- **DECISION:** Compute `expiresAt` on the fly as `createdAt + N days` and do not persist an `expires_at` column — because Criterion 9 requires a config change plus restart to re-expire all links, existing and new. A stored value would go stale, and `created_at` already exists for every row. _(based on: requirements)_
- **DECISION:** Put the expiry logic in one place, a new `LinkExpiryPolicy` component (or equivalent methods on `UrlService`), used by both the service and the controller — because `/shorten`, `/{code}` and `/stats/{code}` all need the same calculation. Duplicating it risks off-by-one differences at the boundary (`>=` versus `>`). _(based on: requirements)_
- **DECISION:** Treat `GET /stats/{code}` as the "link lookup" endpoint. It gains `expiresAt` but keeps returning 200 for expired links — because The requirement only applies expiry to resolution (the redirect). Lookup only has to expose `expiresAt`. _(based on: requirements)_
- **DECISION:** Treat "N days" as `Duration.ofDays(N)`, that is N × 24h on UTC instants — because Criterion 4's example of T + 7d is instant arithmetic. The app clock is UTC, so daylight-saving changes do not apply. _(based on: requirements)_
- **ASSUMPTION:** A redirect to an expired link does NOT increment `click_count` or update `last_accessed_at`. The requirement says no redirect happens, so no click is counted. _(based on: requirements)_
- **ASSUMPTION:** The expiry check and any click recording use a single `clock.instant()` value captured once per request. _(based on: requirements)_
- **ASSUMPTION:** The 410 body is an RFC 9457 problem detail, matching the existing error style. _(based on: requirements)_
- **ASSUMPTION:** The 404 body for unknown codes stays exactly as it is today. _(based on: requirements)_
- **ASSUMPTION:** Other agents add no new dependency (for example `spring-boot-starter-validation`) unless they choose it. Validating in the record's compact constructor works without one. _(based on: requirements)_
- **QUESTION:** Which datasource does production use? The only config shown is in-memory H2 (`jdbc:h2:mem`), so no rows survive a restart and criterion 7 has no real effect at deploy. If production uses a persistent database, how many live links are older than 30 days and would start returning 410 on deploy? This needs human approval. _(based on: requirements)_
- **QUESTION:** Should `/stats/{code}` for an expired link keep returning 200 (assumed here), or should it also signal expiry, for example with an `expired: true` flag or a 410? _(based on: requirements)_
- **QUESTION:** Should a request to an expired link be counted anywhere, such as a separate counter or a log/metric? It is currently assumed to be not counted. _(based on: requirements)_
- **QUESTION:** Should the 410 response carry `Cache-Control: no-store`? Under RFC 9111, 410 is cacheable by default. A cached 410 could outlive an increase of N (criterion 9 revives links). _(based on: requirements)_

## Report

# Impact Analysis: Link Expiry

## Key findings
- **No schema migration and no backfill are needed.** `url_mapping.created_at` already exists as `TIMESTAMP(6) WITH TIME ZONE NOT NULL`, and `UrlMapping.createdAt` maps it. Every existing row already has a value.
- **The `Clock` bean already exists** (`ShortenerApplication.clock()`, `Clock.systemUTC()`). `UrlService` already uses it, so criterion 10 needs no new wiring in production.
- **Short codes cannot be reused today.** `existsByCode` checks every row and nothing deletes rows, so criterion 8 already holds.

## Impacted files

| File | Change | Why |
|---|---|---|
| `config/ShortenerProperties.java` | Add a nested `Link(int expiryDays)` record component, bound to `shortener.link.expiry-days`. Default it to 30 with `@DefaultValue` on the `link` component and on `expiryDays`. Without the defaults, `link` binds as `null` when no `shortener.link.*` property is set. Validate `expiryDays >= 1` in the compact constructor, with a message that contains `shortener.link.expiry-days`. | AC1, AC2. A non-numeric value already fails binding at startup, and Boot's failure analyzer names the property. Zero and negative values need an explicit check. Existing callers (`properties.rateLimit()`, `properties.baseUrl()`) keep compiling because record accessors are unchanged. |
| `service/LinkExpiryPolicy.java` (new) | Takes `ShortenerProperties` and `Clock`. Provides `Instant expiresAt(UrlMapping)` and `boolean isExpired(UrlMapping, Instant now)`, where expired means `now >= expiresAt`. | Single source of truth for AC3, AC4, AC6 and AC9. |
| `service/LinkExpiredException.java` (new) | New runtime exception that carries the code. | AC4/AC5 need a response distinct from `CodeNotFoundException`. |
| `service/UrlService.java` | Constructor gains `LinkExpiryPolicy`. `resolve()` captures `now` once and runs `find(code)`. If the link is expired, it throws `LinkExpiredException` **before** `recordClick`. Otherwise it records the click with the same `now`. `shorten()` and `stats()` keep their behaviour. | AC4, AC7, AC10. |
| `web/ApiExceptionHandler.java` | Add a handler for `LinkExpiredException` that returns `ProblemDetail` with `HttpStatus.GONE`. | AC4. The exception propagates out of the controller before `.location(...)` is called, so no `Location` header is set. |
| `web/dto/ShortenResponse.java` | Append `Instant expiresAt` with an `@Schema` description. | AC6. Additive JSON field. |
| `web/dto/StatsResponse.java` | Append `Instant expiresAt` with an `@Schema` description. | AC6. Additive JSON field. |
| `web/UrlController.java` | `shorten()` and `stats()` populate `expiresAt` from `LinkExpiryPolicy`. `redirect()` adds `@ApiResponse(responseCode = "410", ...)` with a ProblemDetail schema and updates the operation description. | AC6. Keeps the OpenAPI docs accurate. |
| `config/OpenApiConfig.java` | Optional: mention expiry in the API description. | Documentation only. |
| `resources/application.properties` | Optional: add a commented `# shortener.link.expiry-days=30` line. The default must also live in code, so that "not set" really yields 30 (AC1). | Discoverability. |
| `test/.../service/UrlServiceTest.java` | **Will not compile** until it is updated for the new constructor argument. Add tests for T + N − 1s (active), exactly T + N (410), later than T + N (410), and no `recordClick` on an expired link. | AC3, AC4, AC10. |
| `test/.../ShortenerIntegrationTest.java` | Existing tests stay valid because they use a real clock and new links. Add an assertion that `$.expiresAt` exists on `/shorten` and `/stats`, and a check that unknown codes still return 404. | AC5, AC6. |
| New integration test (for example `LinkExpiryIntegrationTest`) | Needs a controllable `Clock`. Bean overriding is off by default in Boot, so use `@TestBean`, `@MockitoBean`, or `@TestConfiguration` with `@Primary`. Seed "pre-existing" rows with an old `createdAt` through `UrlMappingRepository.save(new UrlMapping(code, url, oldInstant))`. Cover a 410 for an old link, a redirect for a recent one, and that the 410 has no `Location` header. | AC4, AC7, AC9, AC10. |
| New properties test (for example with `ApplicationContextRunner`) | Cover startup failure for `0`, `-1` and `abc`, check the message names the property, and check the default of 30. | AC1, AC2. |

## API endpoints
- **`GET /{code}`**: new **410 Gone** response with a problem-detail body and no `Location` header. Responses 302 and 404 are unchanged.
- **`POST /shorten`**: 201 body gains `expiresAt`. All other fields, the `Location` header, and the 400/429 responses are unchanged.
- **`GET /stats/{code}`**: 200 body gains `expiresAt`. It still returns 200 for expired links (see QUESTION). 404 is unchanged.

## Data flow (resolve)
`GET /{code}` → `UrlController.redirect` → `UrlService.resolve`:
1. `now = clock.instant()`.
2. `findByCode`: if missing, throw `CodeNotFoundException` (404).
3. `policy.isExpired(mapping, now)`: if expired, throw `LinkExpiredException` (410), with no click recorded.
4. Otherwise `recordClick(code, now)`, then return a 302.

## Backward-compatibility risks
- **Existing rows:** with the default N = 30, every link older than 30 days returns 410 immediately after deploy (AC7). With the configured in-memory H2 there are no surviving rows, so the real exposure depends on the production datasource. This needs human approval.
- **Existing clients of the redirect:** clients that only handle 302 and 404 will now see 410.
- **Existing clients of the JSON APIs:** `expiresAt` is an added field. Clients that fail on unknown properties will break. That is uncommon but possible.
- **Config changes:** lowering N later expires links in bulk, and raising N revives links that were already expired (AC9). This is intended, but operators should know about it.
- **Timestamp precision:** `Clock.systemUTC()` can return sub-microsecond `Instant`s, but the column is `TIMESTAMP(6)`. The `/shorten` response is built from the in-memory entity, so its `expiresAt` may carry nanoseconds that differ from the value `/stats` later returns from the database. This can break strict equality tests for AC6.
  - Mitigations: compare at microsecond precision in tests, or truncate to `ChronoUnit.MICROS` when creating links. The latter is a behaviour change for review.
- **ISO-8601 format:** confirm that Spring Boot 4 / Jackson 3 serialises `Instant` as an ISO-8601 `...Z` string rather than as numeric timestamps. The existing test only checks `createdAt` is non-empty, so add an explicit format assertion.

## Not impacted (despite appearances)
- **`schema.sql` / `UrlMapping` entity:** no new column, and `ddl-auto=validate` still passes.
- **`UrlMappingRepository`:**
  - No new query is needed, because expiry is checked in Java after `findByCode`.
  - `existsByCode` already blocks reuse of expired codes.
  - Nothing deletes rows, so AC8 already holds.
- **`CodeGenerator`, `UrlValidator`, `InvalidUrlException`:** unchanged.
- **`RateLimitFilter` / `RateLimiter`:** its `isExpired` is about rate-limit windows and is unrelated to link expiry. The filter only covers `POST /shorten`.
- **`ShortenerApplication`:** the `Clock` bean already exists and needs no change.
- **Caching:** there are no in-app caches. The 302 response sets no cache headers and is deliberately not a 301, so nothing keeps serving a redirect past `expiresAt`. The only caching consideration is the 410 itself (see QUESTION).
- **`CodeNotFoundException` / 404 path:** unchanged (AC5).
- **`pom.xml`:** no change needed, unless the implementer chooses Bean Validation (`spring-boot-starter-validation`) over the compact-constructor check.
