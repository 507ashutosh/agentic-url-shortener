#!/usr/bin/env bash
# One-command start for both containers. Needs Docker only.
#
#   ./run.sh                      start the shortener, then run the brownfield demo
#   ./run.sh all [options]        same, with orchestrator options, e.g. --scenario greenfield --auto-approve
#   ./run.sh up                   build and start the shortener in the background (http://localhost:8080)
#   ./run.sh demo [options]       run the orchestrator only (default: --scenario brownfield)
#   ./run.sh all-scenarios [options]   guided tour: greenfield, brownfield, ambiguous, then a summary
#                                 (add --no-pause to run straight through, --auto-approve to skip prompts)
#   ./run.sh metrics              reliability metrics across the runs in ./runs
#   ./run.sh logs                 follow the shortener's log
#   ./run.sh down                 stop everything
set -euo pipefail
cd "$(dirname "$0")"

up() {
  docker compose up -d --build shortener
  printf 'Waiting for the shortener'
  for _ in $(seq 1 60); do
    if curl -fs http://localhost:8080/actuator/health >/dev/null 2>&1; then
      printf ' ready\n\n'
      echo "  API         http://localhost:8080"
      echo "  Swagger UI  http://localhost:8080/swagger-ui.html"
      echo "  Health      http://localhost:8080/actuator/health"
      echo
      echo "  Try: curl -X POST localhost:8080/shorten -H 'Content-Type: application/json' -d '{\"url\":\"https://example.com\"}'"
      echo
      return
    fi
    printf '.'
    sleep 1
  done
  echo
  echo "The shortener did not become healthy within 60 seconds. See: ./run.sh logs" >&2
  exit 1
}

demo() {
  mkdir -p runs
  if [ $# -eq 0 ]; then set -- --scenario brownfield; fi
  docker compose --profile demo build orchestrator
  docker compose --profile demo run --rm orchestrator "$@"
}

# Colours only on a real terminal.
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  BOLD=$'\e[1m'; DIM=$'\e[2m'; CYAN=$'\e[36m'; GREEN=$'\e[32m'; RED=$'\e[31m'; YELLOW=$'\e[33m'; RESET=$'\e[0m'
else
  BOLD=; DIM=; CYAN=; GREEN=; RED=; YELLOW=; RESET=
fi
LINE='════════════════════════════════════════════════════════════════════════'

banner() {   # title, then any number of detail lines
  printf '\n%s%s%s\n' "$CYAN" "$LINE" "$RESET"
  printf '%s  %s%s\n' "$BOLD" "$1" "$RESET"
  shift
  for detail in "$@"; do printf '  %s\n' "$detail"; done
  printf '%s%s%s\n\n' "$CYAN" "$LINE" "$RESET"
}

all_scenarios() {
  local pause=1 args=()
  for a in "$@"; do
    if [ "$a" = "--no-pause" ]; then pause=0; else args+=("$a"); fi
  done
  mkdir -p runs
  docker compose --profile demo build orchestrator

  banner "Agentic SDLC orchestrator · three scenarios" \
    "Each scenario takes one requirement through the same governed pipeline:" \
    "requirements → clarify → plan ∥ impact-analysis → design → implement ∥ tests ∥ docs → validate → release" \
    "${DIM}Agents work on a private copy of the code; each run ends with a tested patch and an audit log.${RESET}"

  local names=(greenfield brownfield ambiguous)
  local titles=("GREENFIELD · build the service from scratch"
                "BROWNFIELD · change an existing system safely"
                "AMBIGUOUS · the requirement is unclear")
  local requirements=("\"Build a URL shortener service… shorten, redirect, count clicks… safe to expose publicly.\""
                      "\"Links should expire after N days.\""
                      "\"Make it more reliable.\"")
  local watch=("Starts from an empty project. Watch: task decomposition, schema + blast-radius approvals, 19 files and 6 test classes generated, then built and tested."
               "Starts from the existing code. Watch: impact analysis of 8 files, the schema change shown as real SQL for approval, implement ∥ tests ∥ docs."
               "Watch: the agent asks questions instead of guessing. At 'clarify', press r to answer them (or y to accept its assumptions).")
  local results=() i

  for i in 0 1 2; do
    banner "Scenario $((i + 1))/3 · ${titles[$i]}" \
      "Requirement: ${requirements[$i]}" \
      "${DIM}${watch[$i]}${RESET}"
    if [ "$pause" = 1 ]; then read -r -p "  Press Enter to start… " _; echo; fi
    local start=$SECONDS outcome
    if docker compose --profile demo run --rm orchestrator --scenario "${names[$i]}" ${args[@]+"${args[@]}"}; then
      outcome="${GREEN}✓ OK${RESET}"
    else
      outcome="${RED}■ STOPPED${RESET}"
    fi
    results+=("$(printf '  %-12s %s   %ss' "${names[$i]}" "$outcome" "$((SECONDS - start))")")
    if [ "$pause" = 1 ] && [ "$i" -lt 2 ]; then echo; read -r -p "  Press Enter for the next scenario… " _; fi
  done

  banner "Summary"
  printf '%s\n' "${results[@]}"
  printf '\n  Each run: %sruns/<run-id>/%s (stage reports, changes.patch, audit.jsonl)\n' "$BOLD" "$RESET"
  printf '  Reliability metrics across all runs: %s./run.sh metrics%s\n\n' "$BOLD" "$RESET"
}

command="${1:-all}"
[ $# -gt 0 ] && shift
case "$command" in
  up)      up ;;
  demo)    demo "$@" ;;
  all)     up; demo "$@" ;;
  all-scenarios) all_scenarios "$@" ;;
  metrics) docker compose --profile demo run --rm orchestrator --metrics ;;
  logs)    docker compose logs -f shortener ;;
  down)    docker compose --profile demo down ;;
  *)       sed -n '2,13p' "$0"; exit 2 ;;
esac
