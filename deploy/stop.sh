#!/usr/bin/env bash
set -euo pipefail

cd /opt/bibexpo 2>/dev/null || exit 0
[ -f compose.yml ] || exit 0

set -a
# shellcheck disable=SC1091
. deploy/image.env
set +a

docker compose down --timeout 60
