# implement

**Summary:** Expiry implemented in 10 files: schema, entity, service, 2 new exceptions, DTOs, controller, error mapping

## Decisions and assumptions

- **DECISION:** Compute expired-state in the controller from the injected Clock — because keeps StatsResponse a plain record and time stays testable _(based on: requirements, design)_

## Files written

- `shortener/src/main/resources/schema.sql`
- `shortener/src/main/java/com/agentic/shortener/domain/UrlMapping.java`
- `shortener/src/main/java/com/agentic/shortener/service/InvalidExpiryException.java`
- `shortener/src/main/java/com/agentic/shortener/service/LinkExpiredException.java`
- `shortener/src/main/java/com/agentic/shortener/service/UrlService.java`
- `shortener/src/main/java/com/agentic/shortener/web/dto/ShortenRequest.java`
- `shortener/src/main/java/com/agentic/shortener/web/dto/ShortenResponse.java`
- `shortener/src/main/java/com/agentic/shortener/web/dto/StatsResponse.java`
- `shortener/src/main/java/com/agentic/shortener/web/UrlController.java`
- `shortener/src/main/java/com/agentic/shortener/web/ApiExceptionHandler.java`

## Report

Implemented exactly as designed. The one-argument `shorten(String)` is kept so existing callers compile unchanged.
