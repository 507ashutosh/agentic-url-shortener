# tests

**Summary:** 2 new test classes: unit tests for the expiry rules and an end-to-end test with a controllable clock

## Decisions and assumptions

- **DECISION:** Replace the clock in the integration test with a @Primary test clock — because lets the test move time forward one day instead of waiting _(based on: requirements, design)_
- **ASSUMPTION:** The service exposes MAX_EXPIRY_DAYS = 3650 and the exception names given in the design _(based on: requirements, design)_

## Files written

- `shortener/src/test/java/com/agentic/shortener/service/UrlServiceExpiryTest.java`
- `shortener/src/test/java/com/agentic/shortener/LinkExpiryIntegrationTest.java`

## Report

Covers acceptance criteria 1-7. Criterion 8 (existing tests still pass) is checked by the validate stage running the full suite.
