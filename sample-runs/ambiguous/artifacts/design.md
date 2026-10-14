# design

**Summary:** Enable liveness/readiness probes, 2 s connection timeout, 20 s graceful shutdown; database-unavailable → 503 with Retry-After: 5

## Decisions and assumptions

- **DECISION:** Separate liveness and readiness probes — because a platform should restart a dead instance but only stop routing to a not-ready one _(based on: requirements, plan, impact-analysis)_
- **DECISION:** Return 503 with Retry-After: 5 when the database is unavailable — because 503 tells clients the failure is temporary and safe to retry; 500 suggests a bug _(based on: requirements, plan, impact-analysis)_
- **DECISION:** Map DataAccessResourceFailureException and CannotCreateTransactionException — because these are what Spring raises when the database cannot be reached, before or inside a transaction _(based on: requirements, plan, impact-analysis)_
- **DECISION:** Hikari connection timeout 2 s — because fail fast so callers get a clear 503 instead of hanging for 30 s _(based on: requirements, plan, impact-analysis)_
- **DECISION:** Graceful shutdown with a 20 s grace period — because in-flight requests finish during deploys; 20 s fits typical platform termination windows _(based on: requirements, plan, impact-analysis)_

## Report

## Configuration (application.properties additions)

```
management.endpoint.health.probes.enabled=true
spring.datasource.hikari.connection-timeout=2000
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```

## Error contract

`503 Service Unavailable`, header `Retry-After: 5`, body:
`{"title": "Service Unavailable", "status": 503, "detail": "The service is temporarily unavailable. Please retry shortly."}`
The internal exception message is not exposed.

## Code

`ApiExceptionHandler`: new handler `databaseUnavailable` for `DataAccessResourceFailureException` and `CannotCreateTransactionException`, returning `ResponseEntity<ProblemDetail>` with the header.
