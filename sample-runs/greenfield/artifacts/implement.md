# implement

**Summary:** Service implemented from scratch: 19 files across domain, service, web, rate limiting and configuration

## Decisions and assumptions

- **DECISION:** Inject a single Clock bean wherever time is read — because lets tests control time without sleeping _(based on: requirements, design)_
- **DECISION:** Keep the controller thin; all rules live in UrlService — because rules are unit-testable without HTTP _(based on: requirements, design)_

## Files written

- `shortener/src/main/java/com/agentic/shortener/ShortenerApplication.java`
- `shortener/src/main/java/com/agentic/shortener/config/OpenApiConfig.java`
- `shortener/src/main/java/com/agentic/shortener/config/ShortenerProperties.java`
- `shortener/src/main/java/com/agentic/shortener/domain/UrlMapping.java`
- `shortener/src/main/java/com/agentic/shortener/domain/UrlMappingRepository.java`
- `shortener/src/main/java/com/agentic/shortener/ratelimit/RateLimitFilter.java`
- `shortener/src/main/java/com/agentic/shortener/ratelimit/RateLimiter.java`
- `shortener/src/main/java/com/agentic/shortener/service/CodeGenerator.java`
- `shortener/src/main/java/com/agentic/shortener/service/CodeNotFoundException.java`
- `shortener/src/main/java/com/agentic/shortener/service/InvalidUrlException.java`
- `shortener/src/main/java/com/agentic/shortener/service/UrlService.java`
- `shortener/src/main/java/com/agentic/shortener/service/UrlValidator.java`
- `shortener/src/main/java/com/agentic/shortener/web/ApiExceptionHandler.java`
- `shortener/src/main/java/com/agentic/shortener/web/UrlController.java`
- `shortener/src/main/java/com/agentic/shortener/web/dto/ShortenRequest.java`
- `shortener/src/main/java/com/agentic/shortener/web/dto/ShortenResponse.java`
- `shortener/src/main/java/com/agentic/shortener/web/dto/StatsResponse.java`
- `shortener/src/main/resources/application.properties`
- `shortener/src/main/resources/schema.sql`

## Report

Implemented the design as specified. Files below are complete.
