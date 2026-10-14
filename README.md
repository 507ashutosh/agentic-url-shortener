# Agentic SDLC Orchestrator — URL shortener

A system of AI agents that takes a software requirement through the full delivery lifecycle — requirements, planning, impact analysis, design, implementation, tests, docs, validation and release — under explicit governance: a dependency graph with gates, human approval checkpoints, bounded retries, fallback, rollback, safe-stop, policy guardrails, a tamper-evident audit trail and reliability metrics.

The system under change is a small URL shortener (Spring Boot). **The orchestrator is the main deliverable**; the shortener is what the agents build and change.

> Principle: agents execute under defined autonomy boundaries; humans own oversight, approvals and final quality.

**Two modes.** By default the agents replay **recorded model responses** (written in advance for the three scenarios), so a run is free, repeatable and needs no API key. Everything around the model is real: prompts, parsing, file writes, policy checks, approvals, rollback, the Maven build and tests, the patch and the audit log. This mode demonstrates the orchestration, not the model's engineering ability. With `--live`, real Claude agents do the work: see the [live run](sample-runs/brownfield-live/), where the agents chose their own design, wrote 17 files and 64 tests passed.

```
                          ┌ plan            ┐            ┌ implement ┐
requirements → clarify → ┤                 ├→ design → ┼ tests     ┼→ validate → release
                          └ impact-analysis ┘            └ docs      ┘
   agent        human        agents (parallel)   agent    agents (parallel)   build+tests   human sign-off
```

## What happens in a run

```
$ ./run.sh demo                 # or: java -jar orchestrator/target/orchestrator.jar --scenario brownfield
Requirement: "Links should expire after N days."
  ✓ requirements     Optional per-link expiry in whole days; expired links … return 410 Gone
  ⏸ approval required · clarify · 2 open questions on the requirement          → accept or answer
  ✓ plan / ✓ impact-analysis (parallel)
  ✓ design           Nullable expires_at column, optional expiresInDays (1-3650), 410 Gone …
  ⏸ approval required · implement · database schema change                        → y/n
      + expires_at       TIMESTAMP(6) WITH TIME ZONE,
  ✓ implement / ✓ tests / ✓ docs (parallel)
  ✓ validate         build OK, 49 tests run, 0 failures, 0 errors                   ← real Maven build
  ⏸ approval required · release · release this change set                         → approve / reject / revise
  ✓ release          5 added, 8 modified, 0 deleted · patch: runs/…/changes.patch
Time: 10.8s total · 7.4s agents and tools · 3.4s waiting for approvals
Audit log: runs/…/audit.jsonl (hash chain verified)
```

Agents never touch the real code: each run works on a private copy in `runs/<run-id>/workspace/` and ends with a reviewable patch.

## Quick start

### Option A — Docker (no Java needed)

You need only **Docker with Compose** (Docker Desktop on macOS/Windows) and a free port 8080. The first run downloads the Java base images and libraries (a few hundred MB); later runs reuse them.

```bash
git clone https://github.com/507ashutosh/agentic-url-shortener.git
cd agentic-url-shortener
./run.sh            # starts the shortener on :8080, then runs the brownfield demo interactively
./run.sh all-scenarios   # or: a guided tour of all three scenarios
```

| Command | Effect |
|---------|--------|
| `./run.sh up` | build and start the shortener in the background (API, Swagger UI, health on :8080) |
| `./run.sh demo [options]` | run the orchestrator, e.g. `./run.sh demo --scenario greenfield --auto-approve` |
| `./run.sh all [options]` | both of the above |
| `./run.sh all-scenarios [options]` | guided tour of greenfield → brownfield → ambiguous: a banner explaining each scenario, a pause before each, and a results table at the end (`--no-pause` runs straight through, `--auto-approve` skips the prompts) |
| `./run.sh metrics` | reliability metrics across all runs |
| `./run.sh logs` / `./run.sh down` | follow the shortener's log / stop everything |

The two containers are independent: the shortener is a long-running service; the orchestrator runs one scenario on its own copy of the code and exits. Run output appears in `./runs`.

- **Your name on approvals:** inside the container the OS user is `agent`; pass `./run.sh demo --approver <your-name>`.
- **Live agents in Docker:** `ANTHROPIC_API_KEY=<your-key> ./run.sh demo --live` (the key is passed through from your shell, never stored).
- **Without bash** (e.g. Windows PowerShell): `docker compose up -d shortener`, then `docker compose run --rm orchestrator --scenario brownfield`.
- Tested on macOS with Docker Desktop, including a fresh clone with no cached images.

### Option B — Java 25

Requires JDK 25 and git. Maven is not needed (the wrapper downloads it).

```bash
./mvnw package                                   # builds both modules and runs all 86 tests
java -jar orchestrator/target/orchestrator.jar --scenario brownfield
```

Run the commands from the repository root.

## The three scenarios

| Scenario | Requirement | What it shows |
|----------|-------------|---------------|
| `greenfield` | Build a URL shortener service (from an empty scaffold) | Decomposition of a new system; schema and blast-radius approvals; 19 files + 6 test classes generated and verified |
| `brownfield` | "Links should expire after N days." | Codebase reasoning: impact analysis of 8 existing files, backward compatibility, a schema change behind approval |
| `ambiguous` | "Make it more reliable." | The requirements agent raises open questions; a human answers them at the `clarify` checkpoint and the requirement is re-derived before any design |

```bash
java -jar orchestrator/target/orchestrator.jar --scenario greenfield
java -jar orchestrator/target/orchestrator.jar --scenario ambiguous
```

### Options

| Option | Effect |
|--------|--------|
| `--auto-approve` | Approve every checkpoint automatically (recorded as `auto` in the audit log) |
| `--approver <name>` | Name recorded on approvals (default: your OS user) |
| `--live` | Real Claude agents instead of recorded responses (needs `ANTHROPIC_API_KEY`; model `claude-opus-5-5`, override with `ANTHROPIC_MODEL`) |
| `--inject-fault <stage>:<kind>` | Chaos testing: `error` (transient), `outage` (model down), `secret` (leaked credential), `broken-code` (does not compile). Repeatable |
| `--metrics` | Reliability metrics across all runs in `runs/` |
| `--verify-audit <audit.jsonl>` | Check an audit log's hash chain |

### See the safety mechanisms

```bash
# retry, secret-scan block with rollback, and model fallback — all recovered
java -jar orchestrator/target/orchestrator.jar --auto-approve \
  --inject-fault tests:error --inject-fault docs:secret --inject-fault design:outage

# validation fails → whole change set rolled back → safe-stop
java -jar orchestrator/target/orchestrator.jar --auto-approve --inject-fault implement:broken-code

# interactive: answer "n" at the schema prompt (rollback + safe-stop),
# or "r" at the release prompt to send a stage back (re-planning re-runs only what is affected)
java -jar orchestrator/target/orchestrator.jar --scenario brownfield

java -jar orchestrator/target/orchestrator.jar --metrics
```

### Mock and live agents

By default agents use **recorded responses** (`orchestrator/src/main/resources/mock/<scenario>/`), so runs are free, repeatable and need no API key. Only the model's reply is recorded: prompts, parsing, file writes, policies, approvals, the Maven build and the patch are all real.

`--live` uses the Claude API through the official Anthropic Java SDK. If the live model fails twice for an agent, a circuit breaker falls back to the recorded response for that agent, and the fallback is recorded in the audit log.

```bash
export ANTHROPIC_API_KEY=...        # never put the key in a file in this repository
java -jar orchestrator/target/orchestrator.jar --scenario brownfield --live
```

## What a run leaves behind

```
runs/<run-id>/
├─ console output        (on screen)
├─ audit.jsonl           every event, hash-chained; verify with --verify-audit
├─ run-summary.json      this run's metrics
├─ changes.patch         the proposed change, applicable with `git apply`
├─ artifacts/*.md        one report per stage: spec, task plan, impact analysis, design, decisions…
└─ workspace/            the changed copy of the code
```

Committed examples are in [`sample-runs/`](sample-runs/README.md): all three scenarios, two failure demonstrations, and a [run with live Claude agents](sample-runs/brownfield-live/) (5 minutes, 17 files changed, 64 tests passing).

## The URL shortener

| Method | Path | Result |
|--------|------|--------|
| `POST` | `/shorten` `{"url": "..."}` | 201 with code and short URL; 400 invalid URL; 429 rate limited |
| `GET` | `/{code}` | 302 redirect (click counted); 404 unknown |
| `GET` | `/stats/{code}` | click count, created and last-accessed times |
| `GET` | `/actuator/health` | health |
| `GET` | `/swagger-ui.html`, `/v3/api-docs` | Swagger UI and OpenAPI spec ([exported copy](docs/openapi.yaml)) |

```bash
java -jar shortener/target/shortener-0.1.0-SNAPSHOT.jar
curl -X POST localhost:8080/shorten -H 'Content-Type: application/json' -d '{"url":"https://example.com"}'
```

## Repository layout

```
├─ orchestrator/          the agentic orchestrator (plain Java 25, no framework)
│  └─ src/main/java/com/agentic/orchestrator/
│     ├─ engine/          dependency-graph engine: scheduling, gates, retries, rollback hooks, safe-stop, re-runs
│     ├─ agent/           LLM agent framework + one class per role (agent/roles/)
│     ├─ governance/      policies, approval gateways, policy/release gates, clarification checkpoint
│     ├─ llm/             model boundary: Claude client, recorded responses, fallback, fault injection
│     ├─ run/             run context, workspace, git checkpoints
│     ├─ observability/   audit log, run metrics, cross-run report
│     ├─ tools/           deterministic stages: Maven validation, change-set builder
│     └─ pipeline/        wires agents, tools and governance into the graph
├─ shortener/             the URL shortener service (Spring Boot 4)
├─ scenarios/             greenfield.yaml, brownfield.yaml, ambiguous.yaml
├─ sample-runs/           committed example runs
├─ docker/                Dockerfiles for both (docker-compose.yml and run.sh at the root)
└─ docs/                  architecture, engineering summary, OpenAPI spec
```

## Documentation

- [Architecture](docs/architecture.md) — components, orchestration model, control flow, key decisions
- [Engineering summary](docs/engineering-summary.md) — plan and rationale, artifacts, testing approach, risks, trade-offs, assumptions, limitations
- [Sample runs](sample-runs/README.md) — real outputs of every scenario and of the failure modes

## Tech

Java 25 · Spring Boot 4.1 (shortener only) · Maven · H2 · JUnit 5, AssertJ, Mockito · Anthropic Java SDK · springdoc-openapi · Docker
