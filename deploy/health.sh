#!/usr/bin/env bash
set -euo pipefail

cd /opt/bibexpo

set -a
# shellcheck disable=SC1091
. deploy/image.env
set +a

# First boot against an empty database runs every migration, so allow a few minutes.
# Stops short of the hook timeout so the logs below still make it into the deployment.
for _ in $(seq 1 55); do
  if curl -fsS --max-time 5 http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done

docker compose logs --tail 200 app || true
exit 1
