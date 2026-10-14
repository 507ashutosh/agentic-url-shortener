# Engineering summary

## 1. Plan and rationale

The brief asks for an agentic system that turns requirements into reviewable engineering outcomes, with orchestration as the critical differentiator. I therefore kept the system under change deliberately small (a URL shortener) and spent most of the effort on the orchestration and its governance.

Build order, each step runnable and tested before the next:

1. **URL shortener**: the baseline the agents change.
2. **Dependency-graph engine**: explicit DAG, parallel waves, synchronization.
3. **Agents**: one class per SDLC responsibility, shared context and decision lineage, mock and live model clients.
4. **Governance**: gates, human approvals, bounded retries, fallback, rollback, safe-stop, policies, re-planning.
5. **Observability**: hash-chained audit log, per-run summaries, cross-run reliability metrics.
6. **Scenarios, packaging, documentation**: greenfield, brownfield and ambiguous scenarios; Docker; OpenAPI; sample runs.

Why build the orchestrator rather than use LangGraph4j, Temporal or Camunda: the orchestration is what is being evaluated, and a small explicit engine (the `engine` package is about 700 lines including comments) makes every mechanism visible and unit-testable. The production path is described in section 6.

How agents were chosen: one agent per distinct responsibility the brief names (requirements, decomposition, codebase reasoning, design, implementation, tests, docs), so each output is a separate, reviewable artifact; validation and release are deterministic tools, because checking work must not depend on a model's opinion.

## 2. Artifacts

| Artifact | Where |
|----------|-------|
| Orchestrator (engine, agents, governance, observability) | `orchestrator/` |
| URL shortener service | `shortener/` |
| API definition | OpenAPI 3.1: `docs/openapi.yaml`, live at `/v3/api-docs` and `/swagger-ui.html` |
| Schema definition | `shortener/src/main/resources/schema.sql` (owned by SQL; Hibernate only validates) |
| Scenarios | `scenarios/*.yaml` |
| Recorded agent responses | `orchestrator/src/main/resources/mock/<scenario>/` |
| Example runs (console, stage reports, decisions, patch, audit log, metrics) | `sample-runs/` |
| Architecture | `docs/architecture.md` |
| Container images | `docker/` |

Per run, the orchestrator produces: a requirement spec with acceptance criteria and assumptions, a task plan with dependencies, an impact analysis, a design with recorded decisions, code, tests and documentation in the workspace, a validation result from a real Maven build, a patch (`changes.patch`), an audit log and a run summary.

## 3. Testing approach

| Level | What | Count |
|-------|------|-------|
| Shortener unit tests | validator, code generator, service rules with mocks, rate limiter with a controllable clock | 26 |
| Shortener integration tests | full app over HTTP with a real H2 database: create, redirect, stats, errors, 429, health, OpenAPI | 9 |
| Engine | ordering, real parallelism (a latch that only releases if stages truly overlap), retries, bounded exhaustion, safe-stop, entry/exit gates, rollback/commit calls, broken gates, re-planning, listener events, DAG validation and cycle detection | 20 |
| Agents | output parsing, prompt content, file writes, decision lineage, autonomy boundary (outside folder, path traversal), real code never touched | 9 |
| Governance | every policy (including masking of secrets), clarification checkpoint outcomes | 10 |
| Rollback | git checkpoints: per-folder rollback that leaves parallel work intact, commit, reset to baseline | 4 |
| Model boundary | circuit-breaker fallback per agent, fault injection | 3 |
| Observability | audit lines and hash chain, tamper detection (edited and deleted events), metrics counting, cross-run aggregation | 5 |

Total: 86 automated tests (`./mvnw package`).

End-to-end checks: every scenario and every injected fault was run through the real pipeline (including the Maven build of the changed code) and the outputs were checked against expectations; they are committed in `sample-runs/`. Each run's patch was checked to apply cleanly with `git apply --check`.

A live run with real Claude agents (`sample-runs/brownfield-live/`) completed the brownfield scenario in about 5 minutes: 8 files added and 9 modified, 64 tests passing, with implementation and tests written independently from the same design. It also showed why governance matters: the agents interpreted "N days" differently from the recorded run (a global setting instead of per link) and raised the risk that existing links would expire on deploy, as open questions at four stages.

Every code change an agent makes is itself validated by the full shortener test suite inside the run (35 existing tests plus the agents' new ones), so a change that breaks existing behaviour cannot reach the release prompt.

## 4. Risks, failure scenarios and the controls for them

| Failure scenario | Control | Demonstrated by |
|------------------|---------|-----------------|
| Model call fails transiently | bounded retry with backoff (3 attempts) | `--inject-fault tests:error` |
| Model unavailable | per-agent circuit breaker → recorded fallback response | `--inject-fault design:outage` |
| Agent leaks a credential | secret-scan policy blocks, attempt rolled back and retried; final scan before release | `--inject-fault docs:secret` |
| Agent writes outside its remit | autonomy boundary rejects the write before anything is written | `LlmAgentTest` |
| Agent weakens tests to pass | test-integrity policy blocks deleted, disabled or removed tests | `PoliciesTest` |
| Generated code does not compile or breaks behaviour | validate stage runs the real build and all tests; whole change set rolled back; safe-stop | `--inject-fault implement:broken-code` |
| High-impact change (schema, dependency, large change) | change-control approval with the actual diff as evidence | every scenario |
| Human rejects a change | rollback of that stage, safe-stop | answer `n` at a prompt |
| Requirement unclear | clarification checkpoint before any design | `ambiguous` scenario |
| Reviewer wants a change late | revise at release → re-planning re-runs only the affected stages | answer `r` at release |
| Implement and tests disagree (parallel, live mode) | validation fails the run; reviewer can revise | live runs |
| Audit trail altered afterwards | SHA-256 hash chain; `--verify-audit` | `AuditLogTest` |
| A gate's check itself fails | treated as stop, never as pass | `DagExecutorTest` |

## 5. Trade-offs

| Choice | Gained | Given up |
|--------|--------|----------|
| Own engine instead of a workflow engine | transparency, testability, no infrastructure | durability across process crashes, distribution, a UI |
| Fixed SDLC graph per run; the planner's task list is an artifact, not the graph | predictable governance; every run has the same gates | the graph does not reshape itself per requirement |
| Whole-file outputs from agents | simple, robust parsing; exact policy checks | more tokens than diffs for large files |
| Validation as one deterministic stage after parallel work | one authoritative check | mismatches between parallel agents are found late |
| Recorded responses for mock mode | free, repeatable demos and tests | mock agents cannot react to new feedback (live ones do) |
| Approval as an exit gate after `implement` | the human sees the real change, not a description | the work is done before approval and rolled back if rejected |
| In-memory rate limiting and H2 in the shortener | simple, self-contained | single-instance only, data lost on restart |
| Java 25 | current language and virtual threads | reviewers need JDK 25 (mitigated by Docker) |

## 6. Assumptions

- The orchestrator runs locally on one machine for one requirement at a time.
- A human is present for interactive runs; unattended runs use `--auto-approve`, which the audit trail marks as such.
- The shortener's interpretation of each scenario's open questions is the agents' recorded assumption (shown in the decision log) unless a human answers at `clarify`.
- "Release" means a reviewed, approved patch; applying it to the repository and deploying are human steps.

## 7. Limitations

- **Mock responses are scenario-specific.** Arbitrary new requirements need `--live`.
- **Not durable.** If the process dies, the run cannot resume; the audit log survives (it is written line by line). Temporal or a persisted state machine would add resumability.
- **No real deployment.** Release stops at an approved patch; there is no CI/CD, environment promotion or production rollback.
- **Policies are pattern-based.** The secret scan uses regular expressions and can miss secrets or flag false positives; a production setup would use gitleaks or similar, plus SAST and licence scanning.
- **Single implement agent per run.** Large changes are not split across agents by module.
- **Only the requirements agent's questions reach a human checkpoint.** Questions raised by later agents (the live run's design agent asked for approval of retroactive expiry) are recorded in the decision log and audit trail but do not yet pause the run; the next step is a gate that routes any open QUESTION to a human before release.
- **Live outputs vary.** A live run can fail validation when parallel agents disagree; the system stops safely, but it does not yet feed validation errors back to the agents automatically.
- **Metrics are local.** Computed from `runs/`; no time-series storage or dashboard.
- **The shortener is intentionally minimal**: no authentication, no custom aliases, in-memory storage.

## 8. Path to production

- Run the same graph on **Temporal** (durable execution, retries and timers as first-class features, horizontal workers); keep policies and agents as activities.
- Replace the console approval gateway with pull-request reviews or a ticketing integration; map approvals to real identities.
- Feed validation failures back to the implement and tests agents as a bounded repair loop.
- Use dedicated scanners (gitleaks, Semgrep, dependency licence checks) as additional policies.
- Ship audit events to an append-only store and metrics to Prometheus/Grafana; alert on success rate and MTTR.
- Let the planner propose the stage graph per requirement, validated against a policy for which gates are mandatory.
