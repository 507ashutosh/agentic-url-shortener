# plan

**Summary:** 9 tasks; schema and domain first, then code, tests and docs in parallel, then validation and release

## Decisions and assumptions

- **DECISION:** Write tests from the design contract in parallel with the implementation — because it shortens the critical path and tests the contract rather than whatever the code happens to do _(based on: requirements)_

## Report

## Tasks

| Id | Task | Stage | Depends on |
|----|------|-------|------------|
| T1 | Define API contract for `expiresInDays`, `expiresAt`, `expired` and the 410 response | design | - |
| T2 | Define schema change: nullable `expires_at` column on `url_mapping` | design | - |
| T3 | Add `expires_at` to schema and `expiresAt` to the `UrlMapping` entity | implement | T2 |
| T4 | Validate `expiresInDays`, compute `expiresAt`, refuse expired links in `UrlService` | implement | T1, T3 |
| T5 | Map new exceptions to 400 / 410 and extend request and response DTOs | implement | T1, T4 |
| T6 | Unit tests for expiry rules in `UrlService` | tests | T1 |
| T7 | Integration tests for create, redirect, 410 and stats with a controllable clock | tests | T1 |
| T8 | Feature documentation with curl examples | docs | T1 |
| T9 | Build and run the full test suite; produce the change set | validate, release | T3-T8 |

## Parallelism

After design (T1, T2), the implementation (T3-T5), tests (T6, T7) and docs (T8) are independent and can run at the same time. T9 waits for all of them.
