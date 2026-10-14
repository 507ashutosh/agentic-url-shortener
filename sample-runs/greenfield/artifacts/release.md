# release

**Summary:** 26 added, 0 modified, 0 deleted · patch: runs/run-20261014-133058-greenfield/changes.patch

## Report

Change set for review (not yet applied to the repository).

- added `docs/features/url-shortener.md`
- added `shortener/src/main/java/com/agentic/shortener/ShortenerApplication.java`
- added `shortener/src/main/java/com/agentic/shortener/config/OpenApiConfig.java`
- added `shortener/src/main/java/com/agentic/shortener/config/ShortenerProperties.java`
- added `shortener/src/main/java/com/agentic/shortener/domain/UrlMapping.java`
- added `shortener/src/main/java/com/agentic/shortener/domain/UrlMappingRepository.java`
- added `shortener/src/main/java/com/agentic/shortener/ratelimit/RateLimitFilter.java`
- added `shortener/src/main/java/com/agentic/shortener/ratelimit/RateLimiter.java`
- added `shortener/src/main/java/com/agentic/shortener/service/CodeGenerator.java`
- added `shortener/src/main/java/com/agentic/shortener/service/CodeNotFoundException.java`
- added `shortener/src/main/java/com/agentic/shortener/service/InvalidUrlException.java`
- added `shortener/src/main/java/com/agentic/shortener/service/UrlService.java`
- added `shortener/src/main/java/com/agentic/shortener/service/UrlValidator.java`
- added `shortener/src/main/java/com/agentic/shortener/web/ApiExceptionHandler.java`
- added `shortener/src/main/java/com/agentic/shortener/web/UrlController.java`
- added `shortener/src/main/java/com/agentic/shortener/web/dto/ShortenRequest.java`
- added `shortener/src/main/java/com/agentic/shortener/web/dto/ShortenResponse.java`
- added `shortener/src/main/java/com/agentic/shortener/web/dto/StatsResponse.java`
- added `shortener/src/main/resources/application.properties`
- added `shortener/src/main/resources/schema.sql`
- added `shortener/src/test/java/com/agentic/shortener/RateLimitIntegrationTest.java`
- added `shortener/src/test/java/com/agentic/shortener/ShortenerIntegrationTest.java`
- added `shortener/src/test/java/com/agentic/shortener/ratelimit/RateLimiterTest.java`
- added `shortener/src/test/java/com/agentic/shortener/service/CodeGeneratorTest.java`
- added `shortener/src/test/java/com/agentic/shortener/service/UrlServiceTest.java`
- added `shortener/src/test/java/com/agentic/shortener/service/UrlValidatorTest.java`
