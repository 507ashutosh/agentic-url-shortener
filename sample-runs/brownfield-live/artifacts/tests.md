# tests

**Summary:** Added expiry tests covering the policy, service, properties binding, a fixed-clock integration test for AC3–AC8 and an API-contract integration test; changed only the constructor call in `UrlServiceTest` so it compiles.

## Decisions and assumptions

- **DECISION:** `UrlServiceTest` changes only the `UrlService` constructor call. The new service tests go in `UrlServiceExpiryTest`. — because The design changes the constructor, so the old file would not compile. The brief says to add new classes rather than rewrite existing ones. _(based on: requirements, design)_
- **DECISION:** The design's `ShortenerIntegrationTest` assertions (`expiresAt` format and the 410 entry in `/v3/api-docs`) are in a new `LinkExpiryApiContractIntegrationTest` instead. — because The brief says to add new classes. The new class uses the same `@SpringBootTest` properties, so the cached context is reused. _(based on: requirements, design)_
- **DECISION:** AC8 "codes never reassigned" is tested end to end with `@MockitoSpyBean CodeGenerator`. The spy is forced to return the code of an expired link first. — because This proves that a code which still exists in storage after expiry is skipped by the real collision check, not just by a mock. _(based on: requirements, design)_
- **DECISION:** The 410 test asserts `title`, `status`, `detail`, `instance` and the `application/problem+json` content type. — because The design's API table lists all of these fields as the contract. _(based on: requirements, design)_
- **ASSUMPTION:** Jackson writes `Instant` values as ISO-8601 strings that match `Instant.toString()` (no fractional seconds when they are zero), as the design's `expiresAt` rules state. _(based on: requirements, design)_
- **ASSUMPTION:** Spring fills in `ProblemDetail.instance` with the request path when the handler leaves it null. _(based on: requirements, design)_
- **ASSUMPTION:** The H2 database `jdbc:h2:mem:shortener` is shared across contexts and test methods in one JVM. Each seeded code is therefore used exactly once: `exp0001` to `exp0006` and `fresh06`. _(based on: requirements, design)_
- **ASSUMPTION:** AC2's "fails to start" is checked at the binding level with `ApplicationContextRunner`, as the design specifies. No full application boot is attempted. _(based on: requirements, design)_
- **QUESTION:** Should a human approve retroactive expiry (AC7) before release? The tests assert it, but the release gate in the design is still open. _(based on: requirements, design)_
- **QUESTION:** AC9 (restart with a new N) is checked only at the policy level, by comparing N=7 and N=30 on the same mapping. Is that enough, or is a restart-style test with two contexts wanted? _(based on: requirements, design)_

## Files written

- `shortener/src/test/java/com/agentic/shortener/service/LinkExpiryPolicyTest.java`
- `shortener/src/test/java/com/agentic/shortener/service/UrlServiceExpiryTest.java`
- `shortener/src/test/java/com/agentic/shortener/service/UrlServiceTest.java`
- `shortener/src/test/java/com/agentic/shortener/config/ShortenerPropertiesTest.java`
- `shortener/src/test/java/com/agentic/shortener/LinkExpiryIntegrationTest.java`
- `shortener/src/test/java/com/agentic/shortener/LinkExpiryApiContractIntegrationTest.java`

## Report

# Test report: link expiry

## Files
| File | Type | Covers |
|---|---|---|
| `service/LinkExpiryPolicyTest.java` | new, unit | rule `now >= createdAt + N days`, AC3/AC4 boundary, AC9 |
| `service/UrlServiceExpiryTest.java` | new, unit (Mockito) | AC3, AC4 (no click recorded), AC5, AC7, AC10, timestamps truncated to microseconds |
| `service/UrlServiceTest.java` | constructor call only | keeps existing tests compiling |
| `config/ShortenerPropertiesTest.java` | new, `ApplicationContextRunner` | AC1 (default 30, explicit value), AC2 (`0`, `-1`, `abc` fail and name the property) |
| `LinkExpiryIntegrationTest.java` | new, `@SpringBootTest` + `@TestBean Clock` | AC3, AC4, AC5, AC6, AC7, AC8 (row kept, stats still 200, code not reused) |
| `LinkExpiryApiContractIntegrationTest.java` | new, `@SpringBootTest` with real clock | AC6 format regex, default N=30 end to end, 410 documented in OpenAPI |

## Notes
- None of the tests wait in real time. Time comes from `Clock.fixed`.
- The integration tests reset the `CodeGenerator` spy after each test (the Mockito default), so other tests get real random codes.
