# implement

**Summary:** Implemented global N-day link expiry as designed: the `shortener.link.expiry-days` property (default 30, minimum 1), `LinkExpiryPolicy`, `LinkExpiredException` mapped to 410, `expiresAt` on `/shorten` and `/stats`, and the OpenAPI and properties docs.

## Decisions and assumptions

- **DECISION:** Changed the `UrlService` constructor to the design's exact 5-argument signature and did not keep the old 4-argument one — because The design fixes the constructor and the tests agent is updating `UrlServiceTest` to match. A second constructor would need `@Autowired` to pick one and an extra default `LinkExpiryPolicy`, which the design does not ask for. _(based on: requirements, design)_
- **DECISION:** `resolve` reads `clock.instant()` once, before the lookup, and uses that value for both the expiry check and `recordClick` — because The design lists this step order, and one instant keeps the expiry decision and the recorded access time consistent. _(based on: requirements, design)_
- **DECISION:** The 410 body uses `ProblemDetail.forStatusAndDetail`, the same as the existing 404 handler — because Spring MVC fills `instance` with the request path and sets `application/problem+json`, which matches the contract `{"type":"about:blank","title":"Gone","status":410,...,"instance":"/<code>"}`. _(based on: requirements, design)_
- **DECISION:** Added no `Cache-Control` headers — because The 302 response has never sent cache headers and the design leaves `redirect` unchanged, so nothing can cache a redirect past `expiresAt`. _(based on: requirements, design)_
- **ASSUMPTION:** No production code calls the old two-argument `ShortenerProperties(baseUrl, rateLimit)` constructor; I checked only `src/main`. Tests that build the record directly must pass a `Link`, as the design's `LinkExpiryPolicyTest` already does. _(based on: requirements, design)_
- **ASSUMPTION:** Boot 4 with Jackson 3 serialises `Instant` as ISO-8601 with a `Z` suffix and trims trailing zero fractions. This is already the case for `createdAt` today. `shorten` truncates to microseconds, so at most 6 fractional digits appear. _(based on: requirements, design)_
- **QUESTION:** Retroactive expiry (AC7) will break every existing link older than 30 days as soon as this is deployed. Does a human approve this behaviour and the default N=30 for release, or should the first rollout use a larger N? _(based on: requirements, design)_
- **QUESTION:** Should the old 4-argument `UrlService` constructor stay as a deprecated overload for any callers outside this repository? I assumed there are none. _(based on: requirements, design)_

## Files written

- `shortener/src/main/java/com/agentic/shortener/config/ShortenerProperties.java`
- `shortener/src/main/java/com/agentic/shortener/service/LinkExpiryPolicy.java`
- `shortener/src/main/java/com/agentic/shortener/service/LinkExpiredException.java`
- `shortener/src/main/java/com/agentic/shortener/service/UrlService.java`
- `shortener/src/main/java/com/agentic/shortener/web/ApiExceptionHandler.java`
- `shortener/src/main/java/com/agentic/shortener/web/dto/ShortenResponse.java`
- `shortener/src/main/java/com/agentic/shortener/web/dto/StatsResponse.java`
- `shortener/src/main/java/com/agentic/shortener/web/UrlController.java`
- `shortener/src/main/java/com/agentic/shortener/config/OpenApiConfig.java`
- `shortener/src/main/resources/application.properties`

## Report

# Implementation report: link expiry

## What changed (all under `shortener/src/main`)

| File | Change |
|---|---|
| `config/ShortenerProperties.java` | Added the nested `Link(@DefaultValue("30") int expiryDays)` with validation for values below 1. It is bound through `@DefaultValue Link link`. |
| `service/LinkExpiryPolicy.java` | New. Provides `expiresAt(mapping)` and `isExpired(mapping, now)`. A link is expired when `now >= createdAt + N days`. |
| `service/LinkExpiredException.java` | New. Its message is `Short link for code '<code>' has expired`. |
| `service/UrlService.java` | New 5-argument constructor. `shorten` saves `createdAt` truncated to microseconds. `resolve` throws `LinkExpiredException` before recording a click. |
| `web/ApiExceptionHandler.java` | Added a `LinkExpiredException` handler that returns 410 as a problem detail. |
| `web/dto/ShortenResponse.java`, `web/dto/StatsResponse.java` | Appended `Instant expiresAt` with the design's `@Schema`. |
| `web/UrlController.java` | Injects `LinkExpiryPolicy` and fills `expiresAt` on both responses. The `redirect` description is updated and a 410 `@ApiResponse` is added. |
| `config/OpenApiConfig.java` | Appended the expiry sentence to the API description. |
| `resources/application.properties` | Added the commented-out `shortener.link.expiry-days` documentation lines. |

Unchanged: `UrlMapping`, `UrlMappingRepository`, `schema.sql`, rate limiting, `ShortenerApplication` and `pom.xml`.

## Behaviour notes
- On the expired path, `recordClick` is never called, so `clickCount` and `lastAccessedAt` stay as they were.
- Unknown codes still throw `CodeNotFoundException` and return 404, because the lookup runs before the expiry check.
- `/stats/{code}` does not check expiry, so it keeps returning 200 for expired links.
