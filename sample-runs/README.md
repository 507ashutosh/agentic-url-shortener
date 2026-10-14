# Sample runs

Real outputs of the orchestrator, committed so they can be reviewed without running anything. Each folder holds:

| File | Content |
|------|---------|
| `console.txt` | what the terminal showed |
| `artifacts/*.md` | one report per stage: requirement spec, task plan, impact analysis, design, decisions and assumptions with lineage, validation result, change set |
| `changes.patch` | the proposed code change (GitHub renders it as a diff) |
| `audit.jsonl` | the hash-chained audit trail; check with `--verify-audit sample-runs/<run>/audit.jsonl` |
| `run-summary.json` | the run's metrics |

The full working copy (`workspace/`) is left out; the patch shows every changed line.

| Run | Agents | What to look at | Result |
|-----|--------|-----------------|--------|
| [greenfield](greenfield/) | recorded | service built from an empty scaffold: 19 files + 6 test classes; schema and blast-radius approvals | OK, 35 tests pass |
| [brownfield](brownfield/) | recorded | impact analysis of existing code; schema change approved by a human; parallel implement/tests/docs | OK, 49 tests pass |
| [ambiguous](ambiguous/) | recorded | "Make it more reliable": 3 open questions answered by a human at `clarify`, requirement re-derived, then delivered | OK, 38 tests pass |
| [**brownfield-live**](brownfield-live/) | **live Claude (claude-opus-5-5)** | the same pipeline with real agents: they chose a different but valid design (a global expiry setting), wrote 17 files and new tests independently of each other, and repeatedly raised a real risk (retroactive expiry of existing links) as open questions | OK, 64 tests pass, 5 min |
| [brownfield-recovered-failures](brownfield-recovered-failures/) | recorded + injected faults | model outage → fallback; transient error → retry; leaked secret → blocked, rolled back, retried | OK: 4 retries, 1 rollback, 1 fallback |
| [brownfield-safe-stop](brownfield-safe-stop/) | recorded + injected fault | generated code does not compile → validation fails → 13 files rolled back to baseline → release never starts | STOPPED |

Recorded agents replay stored model replies; everything else (prompts, parsing, file writes, policies, approvals, the Maven build, the patch, the audit log) is real. Interactive runs were answered through the normal approval prompts, with the answers piped in for repeatability (approver name `reviewer`); `auto` marks runs with `--auto-approve`.

## Metrics across these runs

[`metrics.txt`](metrics.txt) is the output of `--metrics` over the runs above.
