# tests

**Summary:** 6 test classes: unit tests per component and integration tests over HTTP with a real database

## Decisions and assumptions

- **DECISION:** Test the rate limiter with a controllable clock instead of waiting — because fast and deterministic _(based on: requirements, design)_
- **DECISION:** Integration tests start the full application with MockMvc — because they exercise routing, validation, persistence and error mapping together _(based on: requirements, design)_
- **ASSUMPTION:** The design's class and method names are used exactly as written _(based on: requirements, design)_

## Files written

- `shortener/src/test/java/com/agentic/shortener/RateLimitIntegrationTest.java`
- `shortener/src/test/java/com/agentic/shortener/ShortenerIntegrationTest.java`
- `shortener/src/test/java/com/agentic/shortener/ratelimit/RateLimiterTest.java`
- `shortener/src/test/java/com/agentic/shortener/service/CodeGeneratorTest.java`
- `shortener/src/test/java/com/agentic/shortener/service/UrlServiceTest.java`
- `shortener/src/test/java/com/agentic/shortener/service/UrlValidatorTest.java`

## Report

Covers acceptance criteria 1-9.
