# design

**Summary:** Technical design for global N-day link expiry. It covers the exact config property, the new `LinkExpiryPolicy` and `LinkExpiredException`, the changed constructor and method signatures, the 410 contract, the `expiresAt` field on two DTOs, no schema change, and a test and docs split that the implement, tests and docs agents can follow in parallel.

## Decisions and assumptions

- **DECISION:** No schema change, migration or backfill. Expiry is computed as `createdAt + N days`, never stored. — because `url_mapping.created_at` already exists and is NOT NULL for every row. Computing expiry at read time also makes AC9 (changing N and restarting re-evaluates all links) automatic. _(based on: requirements, plan, impact-analysis)_
- **DECISION:** Add a nested record `ShortenerProperties.Link(int expiryDays)` as a third component `link` of `ShortenerProperties`. Put `@DefaultValue` on the `link` component and `@DefaultValue("30")` on `expiryDays`. A compact constructor throws `IllegalArgumentException` when the value is below 1. — because This meets AC1 and AC2 without adding a dependency. A non-numeric value already fails binding with a `BindException` that names `shortener.link.expiry-days`. Existing accessors stay source-compatible. _(based on: requirements, plan, impact-analysis)_
- **DECISION:** "N days" means `Duration.ofDays(N)` (N × 24h) added to the UTC `Instant`. A link is expired when `!now.isBefore(expiresAt)`, which is the same as `now >= expiresAt`. — because This matches AC3 and AC4 exactly (T+7d−1s is active, T+7d is expired). Instants have no DST or calendar ambiguity. _(based on: requirements, plan, impact-analysis)_
- **DECISION:** New `@Component` `LinkExpiryPolicy(ShortenerProperties)` with `Instant expiresAt(UrlMapping)` and `boolean isExpired(UrlMapping, Instant now)`. It holds no `Clock`. — because It is the single source of truth for the rule. `now` always comes from the injected `Clock` in `UrlService`, which satisfies AC10 and keeps the policy a pure, trivially unit-testable function. _(based on: requirements, plan, impact-analysis)_
- **DECISION:** `UrlService.resolve` reads `now` once, looks up the link (404 if missing), then throws `LinkExpiredException` before `recordClick` if the link is expired. — because AC5 keeps unknown codes at 404, distinct from 410. Expired links must not count clicks. Throwing before the controller calls `.location(...)` guarantees there is no `Location` header (AC4). _(based on: requirements, plan, impact-analysis)_
- **DECISION:** `LinkExpiredException` maps to `ProblemDetail` with status 410 in `ApiExceptionHandler`. — because This is consistent with the existing RFC 9457 error bodies. _(based on: requirements, plan, impact-analysis)_
- **DECISION:** Append `Instant expiresAt` as the last component of `ShortenResponse` and `StatsResponse`. The controller fills it from `LinkExpiryPolicy`. — because AC6 requires an additive field with existing fields unchanged. Appending keeps the existing JSON order. _(based on: requirements, plan, impact-analysis)_
- **DECISION:** `UrlService.shorten` truncates `clock.instant()` to `ChronoUnit.MICROS` before saving. — because The column is `TIMESTAMP(6)`. Truncating makes `expiresAt` from `POST /shorten` (built from the in-memory entity) equal `expiresAt` from `GET /stats` (read from the DB). Existing tests use whole-second clocks and are unaffected. _(based on: requirements, plan, impact-analysis)_
- **DECISION:** `GET /stats/{code}` keeps returning 200 for expired links, with their `expiresAt`. — because The requirement only changes resolution. AC8 keeps expired links stored, and stats is the "link lookup" response in AC6. _(based on: requirements, plan, impact-analysis)_
- **DECISION:** No cache headers are added to the 410 response in this change. — because The requirement does not ask for them. This is raised as a QUESTION because a cached 410 could outlive a later increase of N. _(based on: requirements, plan, impact-analysis)_
- **ASSUMPTION:** The "link lookup response" in AC6 is `GET /stats/{code}` (`StatsResponse`). There is no other lookup endpoint. _(based on: requirements, plan, impact-analysis)_
- **ASSUMPTION:** Spring Boot 4 / Jackson 3 serialises `Instant` as an ISO-8601 string ending in `Z` (Jackson 3 defaults `WRITE_DATES_AS_TIMESTAMPS` to false). Tests must assert the format explicitly. If this assumption fails, the implement agent must configure it, not change the contract. _(based on: requirements, plan, impact-analysis)_
- **ASSUMPTION:** "Names the property" in AC2 is satisfied if the startup failure's exception chain or stack trace contains `shortener.link.expiry-days`. It is not required to be in the top-level message. _(based on: requirements, plan, impact-analysis)_
- **ASSUMPTION:** Expired links are not counted as clicks, and resolving them does not update `lastAccessedAt`. _(based on: requirements, plan, impact-analysis)_
- **ASSUMPTION:** The 410 body is the default `ProblemDetail` rendered as `application/problem+json`, with no extra fields such as `expiresAt`. _(based on: requirements, plan, impact-analysis)_
- **QUESTION:** Do you approve retroactive expiry (AC7)? With the default N=30, every link older than 30 days returns 410 immediately on deploy. What datasource does production use? The configured H2 is in-memory, so the real exposure depends on that. _(based on: requirements, plan, impact-analysis)_
- **QUESTION:** Should the 410 response send `Cache-Control: no-store`? Without it, raising N later (AC9) may not revive links for clients or proxies that cached the heuristically cacheable 410. _(based on: requirements, plan, impact-analysis)_
- **QUESTION:** Should `GET /stats/{code}` behave differently for expired links, for example by adding an `expired` boolean, instead of returning a normal 200? _(based on: requirements, plan, impact-analysis)_

## Report

# Design: Link Expiry After N Days

## 1. Rule
- `expiresAt(link) = link.createdAt + Duration.ofDays(N)`.
- `N = shortener.link.expiry-days`. It must be at least 1 and defaults to 30.
- A link is **expired** iff `now >= expiresAt`. `now` is read from the injected `java.time.Clock` bean.

## 2. API contract

### `GET /{code}` (code matches `[A-Za-z0-9]{4,10}`)

| Case | Status | Headers | Body |
|---|---|---|---|
| Active (`now < expiresAt`) | 302 | `Location: <originalUrl>` | none (unchanged) |
| Expired (`now >= expiresAt`) | **410** | **no `Location`**, `Content-Type: application/problem+json` | `{"type":"about:blank","title":"Gone","status":410,"detail":"Short link for code '<code>' has expired","instance":"/<code>"}` |
| Unknown code | 404 | unchanged | unchanged |

On the expired path no click is recorded: `clickCount` and `lastAccessedAt` stay unchanged.

### `POST /shorten`, 201 response body
The status, `Location` header and the 400 and 429 responses are unchanged. The body is:
```json
{
  "code": "Ab3dE9x",
  "shortUrl": "http://localhost:8080/Ab3dE9x",
  "originalUrl": "https://example.com/some/long/path",
  "expiresAt": "2026-11-13T10:00:00Z"
}
```

### `GET /stats/{code}`, 200 response body
This also returns 200 for expired links. 404 is unchanged.
```json
{
  "code": "Ab3dE9x",
  "originalUrl": "https://example.com/some/long/path",
  "clickCount": 3,
  "createdAt": "2026-10-14T10:00:00Z",
  "lastAccessedAt": "2026-10-15T09:00:00Z",
  "expiresAt": "2026-11-13T10:00:00Z"
}
```

`expiresAt` rules:
- It is an ISO-8601 UTC string ending in `Z`.
- Fractional seconds appear only when they are non-zero, with at most 6 digits.
- It always equals `createdAt` plus N×24h.

## 3. Configuration
- Property: `shortener.link.expiry-days`, an integer of at least 1. The default is 30 and is set in code.

| Configured value | Result |
|---|---|
| unset | N = 30 |
| `0`, `-1` | Startup fails. `IllegalArgumentException("shortener.link.expiry-days must be at least 1, but was <value>")` is thrown from the `Link` compact constructor, wrapped in a binding exception. |
| `abc` | Startup fails. Boot's `BindException` reports "Failed to bind properties under 'shortener.link.expiry-days' to int". |

## 4. Schema
- **No change.** `schema.sql` and `UrlMapping` are untouched, and `ddl-auto=validate` still passes.

## 5. Code changes (exact names)

All paths are under `shortener/src/main/java/com/agentic/shortener/`.

### `config/ShortenerProperties.java` (modified)
```java
@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(String baseUrl, RateLimit rateLimit, @DefaultValue Link link) {
    public record RateLimit(int requests, Duration window) { }
    public record Link(@DefaultValue("30") int expiryDays) {
        public Link {
            if (expiryDays < 1) {
                throw new IllegalArgumentException(
                    "shortener.link.expiry-days must be at least 1, but was " + expiryDays);
            }
        }
    }
}
```
`@DefaultValue` is `org.springframework.boot.context.properties.bind.DefaultValue`.

### `service/LinkExpiryPolicy.java` (new)
```java
@Component
public class LinkExpiryPolicy {
    public LinkExpiryPolicy(ShortenerProperties properties)  // stores Duration.ofDays(properties.link().expiryDays())
    public Instant expiresAt(UrlMapping mapping)              // mapping.getCreatedAt().plus(expiry)
    public boolean isExpired(UrlMapping mapping, Instant now) // !now.isBefore(expiresAt(mapping))
}
```

### `service/LinkExpiredException.java` (new)
```java
public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String code) {
        super("Short link for code '" + code + "' has expired");
    }
}
```

### `service/UrlService.java` (modified)
- **Constructor (exact order):** `UrlService(UrlMappingRepository repository, UrlValidator validator, CodeGenerator codeGenerator, LinkExpiryPolicy expiryPolicy, Clock clock)`.
- **`shorten(String url)`:**
  - Unchanged except that it saves with `clock.instant().truncatedTo(ChronoUnit.MICROS)`.
- **`resolve(String code)`:**
  1. `Instant now = clock.instant();`
  2. `UrlMapping mapping = find(code);` This throws `CodeNotFoundException` if the code is missing.
  3. `if (expiryPolicy.isExpired(mapping, now)) throw new LinkExpiredException(code);`
  4. `repository.recordClick(code, now);`
  5. `return mapping.getOriginalUrl();`
- **`stats(String code)`:** unchanged.

### `web/ApiExceptionHandler.java` (modified)
Add:
```java
@ExceptionHandler(LinkExpiredException.class)
ProblemDetail gone(LinkExpiredException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
}
```

### `web/dto/ShortenResponse.java` (modified)
- Append `@Schema(description = "When the link stops redirecting: creation time plus the configured expiry (ISO-8601 UTC)", example = "2026-11-13T10:00:00Z") Instant expiresAt`.

### `web/dto/StatsResponse.java` (modified)
- Append `Instant expiresAt` after `lastAccessedAt`, with the same `@Schema` as above.

### `web/UrlController.java` (modified)
- **Constructor:** `UrlController(UrlService urlService, ShortenerProperties properties, LinkExpiryPolicy expiryPolicy)`.
- **`shorten`:**
  - Builds `new ShortenResponse(code, shortUrl, originalUrl, expiryPolicy.expiresAt(mapping))`.
- **`stats`:**
  - Builds `new StatsResponse(..., mapping.getLastAccessedAt(), expiryPolicy.expiresAt(mapping))`.
- **`redirect`:**
  - The method body is unchanged.
  - Change the `@Operation` description to: "Redirects to the original URL and counts the click. Returns 410 once the link is older than the configured expiry (shortener.link.expiry-days, default 30 days)."
  - Add `@ApiResponse(responseCode = "410", description = "Link has expired", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))`.

### `config/OpenApiConfig.java` (modified)
- Append this sentence to the description: "Links expire a configured number of days after creation; following an expired link returns 410 Gone."

### `resources/application.properties` (modified)
- Add these two lines:
  ```
  # Days after creation until a link returns 410 Gone (minimum 1, default 30).
  # shortener.link.expiry-days=30
  ```
- The line stays commented out so the in-code default is what gets exercised.

### Unchanged
- `UrlMapping`
- `UrlMappingRepository`
- `schema.sql`
- `CodeGenerator`
- `UrlValidator`
- rate limiting
- `ShortenerApplication`, which already provides the `Clock` bean
- `pom.xml`

## 6. Tests (tests agent)

All paths are under `shortener/src/test/java/com/agentic/shortener/`.

### `service/LinkExpiryPolicyTest.java` (new)
- Build the policy with `new LinkExpiryPolicy(new ShortenerProperties(null, null, new ShortenerProperties.Link(7)))`.
- With `T = 2026-10-14T10:00:00Z`:
  - `expiresAt` equals T+7d.
  - T+7d−1s is not expired.
  - T+7d is expired.
  - T+8d is expired.
- AC9: the same mapping is expired with N=7 but not with N=30 at T+10d.

### `service/UrlServiceTest.java` (update)
- Use the new constructor, with a real `LinkExpiryPolicy` (N=7) and `Clock.fixed(NOW, UTC)`.
- Existing tests: they keep passing as long as their mappings use `createdAt = NOW`.
- Add tests:
  - A link with `createdAt = NOW − 7d + 1s` resolves and records the click.
  - `createdAt = NOW − 7d` throws `LinkExpiredException`, and `recordClick` is never called.
  - `createdAt = NOW − 30d` throws `LinkExpiredException`.
  - An unknown code still throws `CodeNotFoundException`.

### `config/ShortenerPropertiesTest.java` (new)
- Use `ApplicationContextRunner` with a user configuration annotated `@EnableConfigurationProperties(ShortenerProperties.class)`.
- **Unset:** `link().expiryDays()` is 30.
- **`0`, `-1`, `abc`:** `assertThat(context).hasFailed()`, and `assertThat(context.getStartupFailure()).hasStackTraceContaining("shortener.link.expiry-days")`.

### `LinkExpiryIntegrationTest.java` (new)
- **Setup:**
  - Annotate the class with `@SpringBootTest(properties = {"shortener.link.expiry-days=7", "shortener.rate-limit.requests=1000"})` and `@AutoConfigureMockMvc`.
  - Replace the clock with `@TestBean Clock clock` plus a `static Clock clock()` factory returning `Clock.fixed(NOW, ZoneOffset.UTC)`.
  - Seed rows with `UrlMappingRepository.save(new UrlMapping(code, url, createdAt))`.
  - Seeded codes must be unique alphanumerics of 4–10 characters, for example `exp0001`. All contexts share the H2 database `jdbc:h2:mem:shortener` within one JVM.
- **Cover:**
  - **AC4/AC7:** for `createdAt = NOW − 7d` and `NOW − 30d`:
    - status 410
    - `header().doesNotExist("Location")`
    - `$.status` is 410
    - `$.detail` matches the message above
  - **AC3/AC7:** `createdAt = NOW − 7d + 1s` returns 302 with the `Location` header.
  - **AC8:**
    - After a 410, `repository.findByCode` is still present.
    - `GET /stats/{code}` returns 200, with `clickCount` 0 and `expiresAt` equal to `"<createdAt+7d>"`.
  - **AC5:** `GET /nosuch1` returns 404.
  - **AC6:** `POST /shorten` returns `$.expiresAt` equal to `NOW.plus(7d).toString()` (`"2026-10-21T10:00:00Z"` for `NOW = 2026-10-14T10:00:00Z`). `/stats` returns the same value.

### `ShortenerIntegrationTest.java` (update)
- Assert that `$.expiresAt` exists and matches `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?Z$` on both `/shorten` and `/stats`.
- Assert that `/v3/api-docs` documents `$.paths['/{code}'].get.responses['410']`.

## 7. Docs (docs agent)
- **Code-level docs are owned by the implement agent.** These are the OpenAPI annotations, `OpenApiConfig` and the `application.properties` comment. The docs agent must not edit those files.
- **The docs agent writes the README section and the CHANGELOG entry, which must cover:**
  - the `shortener.link.expiry-days` property (minimum 1, default 30)
  - the rule `now >= createdAt + N×24h` gives 410 Gone with no `Location` header, and expired clicks are not counted
  - the new `expiresAt` field on `/shorten` and `/stats`
  - `/stats` still works for expired links
  - unknown codes still return 404
  - **retroactive effect:** existing links older than N days break at deploy
  - changing N re-evaluates all links on restart, so raising N revives expired links
  - clients that reject unknown JSON fields need updating

## 8. Release gate
Human approval is required for retroactive expiry before release (see QUESTION).
