#!/usr/bin/env bash
set -euo pipefail

cd /opt/bibexpo

set -a
# shellcheck disable=SC1091
. deploy/image.env
set +a

aws ecr get-login-password --region "$AWS_DEFAULT_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

# stop.sh has already removed the container, so this clears every old image off the 8 GiB disk
# before the new one lands; a rollback pulls its image back from ECR.
docker image prune -af
docker compose pull
docker compose up -d
