#!/usr/bin/env bash
# One-command start for both containers. Needs Docker only.
#
#   ./run.sh                      start the shortener, then run the brownfield demo
#   ./run.sh all [options]        same, with orchestrator options, e.g. --scenario greenfield --auto-approve
#   ./run.sh up                   build and start the shortener in the background (http://localhost:8080)
#   ./run.sh demo [options]       run the orchestrator only (default: --scenario brownfield)
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

command="${1:-all}"
[ $# -gt 0 ] && shift
case "$command" in
  up)      up ;;
  demo)    demo "$@" ;;
  all)     up; demo "$@" ;;
  metrics) docker compose --profile demo run --rm orchestrator --metrics ;;
  logs)    docker compose logs -f shortener ;;
  down)    docker compose --profile demo down ;;
  *)       sed -n '2,11p' "$0"; exit 2 ;;
esac
