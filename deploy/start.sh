#!/usr/bin/env bash
set -euo pipefail

cd /opt/bibexpo

set -a
# shellcheck disable=SC1091
. deploy/image.env
set +a

aws ecr get-login-password --region "$AWS_DEFAULT_REGION" \
  | docker login --username AWS --password-stdin "$ECR_REGISTRY"

docker compose pull
docker compose up -d
