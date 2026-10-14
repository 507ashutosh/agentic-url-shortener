# implement

**Summary:** Probes, fail-fast connection timeout and graceful shutdown configured; database-unavailable errors now return 503 with Retry-After

## Decisions and assumptions

- **DECISION:** Do not include the exception message in the 503 body — because it can reveal internal details such as database URLs _(based on: requirements, design)_

## Files written

- `shortener/src/main/resources/application.properties`
- `shortener/src/main/java/com/agentic/shortener/web/ApiExceptionHandler.java`

