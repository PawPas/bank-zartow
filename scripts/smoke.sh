#!/usr/bin/env bash
# Production-image smoke test (T-005 acceptance criteria 1-3).
#
# Builds the Docker image and runs it exactly as `make run` does (same
# docker-compose.yml) against an ephemeral PostgreSQL, then checks:
#   - the container reports the healthcheck UP (criterion 2),
#   - the start page is served by the backend (criterion 1),
#   - /api does not receive the SPA HTML fallback,
#   - the final image contains no source code or build tools (criterion 3).
#
# Everything is hermetic: a per-run Compose project name and a throwaway env
# file with nonsecret credentials, so the developer's own `.env`, containers and
# volumes are never read or touched. The trap removes only resources created
# here (`down -v --rmi local`).
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT="bankzartow-smoke-$$"
ENV_FILE="$(mktemp "${TMPDIR:-/tmp}/bankzartow-smoke-env.XXXXXX")"
HOST_PORT="${SMOKE_HOST_PORT:-18099}"
COMPOSE=(docker compose -p "$PROJECT" --env-file "$ENV_FILE")

cleanup() {
  "${COMPOSE[@]}" down -v --rmi local --remove-orphans >/dev/null 2>&1 || true
  rm -f "$ENV_FILE"
}
trap cleanup EXIT

cat > "$ENV_FILE" <<EOF
POSTGRES_DB=bankdb
POSTGRES_USER=bankuser
POSTGRES_PASSWORD=smoke_ephemeral_pw
POSTGRES_PORT=15499
PORT=$HOST_PORT
JAVA_OPTS=-XX:MaxRAMPercentage=75.0
EOF

echo "[smoke] building image and starting app + PostgreSQL (project $PROJECT)"
"${COMPOSE[@]}" up --build -d

echo "[smoke] waiting for the app healthcheck to report UP"
health=""
deadline=$((SECONDS + 180))
while [ "$SECONDS" -lt "$deadline" ]; do
  health="$(curl -fsS "http://localhost:$HOST_PORT/actuator/health" 2>/dev/null || true)"
  case "$health" in *'"status":"UP"'*) break ;; esac
  sleep 3
done
case "$health" in
  *'"status":"UP"'*) echo "[smoke] health UP" ;;
  *)
    echo "[smoke] FAIL: health not UP (last: '${health:-no response}')"
    "${COMPOSE[@]}" logs app | tail -60
    exit 1
    ;;
esac

echo "[smoke] checking start page is served by the backend"
home="$(curl -fsS "http://localhost:$HOST_PORT/")"
case "$home" in
  *'<div id="root"'*) echo "[smoke] start page served" ;;
  *) echo "[smoke] FAIL: start page did not contain the app root element"; exit 1 ;;
esac

echo "[smoke] checking /api does not receive the SPA HTML fallback"
code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$HOST_PORT/api")"
[ "$code" = "404" ] || { echo "[smoke] FAIL: /api returned $code, expected 404"; exit 1; }
echo "[smoke] /api -> 404"

echo "[smoke] inspecting the final image for sources and build tools"
image="$("${COMPOSE[@]}" images -q app)"
[ -n "$image" ] || { echo "[smoke] FAIL: could not resolve the built app image"; exit 1; }
docker run --rm --entrypoint sh "$image" -c '
  set -e
  for tool in javac mvn mvnw node npm; do
    if command -v "$tool" >/dev/null 2>&1; then
      echo "FAIL: build tool present in image: $tool"; exit 1
    fi
  done
  hit="$(find / -xdev \( -name "*.java" -o -name "pom.xml" -o -name "*.tsx" -o -name "mvnw" \) 2>/dev/null | head -n 1)"
  if [ -n "$hit" ]; then echo "FAIL: source/build file present in image: $hit"; exit 1; fi
  echo "[smoke] image contains only runtime artifacts"
'

echo "[smoke] PASS"
