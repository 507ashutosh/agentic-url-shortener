# tests

**Summary:** 1 new integration test class: both probes report UP, and database-unavailable failures map to 503 with Retry-After

## Decisions and assumptions

- **DECISION:** Simulate the database outage by making the service throw the Spring exceptions — because stopping a real database inside a unit test run is slow and flaky; the contract under test is the HTTP mapping _(based on: requirements, design)_

## Files written

- `shortener/src/test/java/com/agentic/shortener/ReliabilityIntegrationTest.java`

