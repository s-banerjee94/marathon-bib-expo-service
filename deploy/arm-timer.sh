#!/usr/bin/env bash
# Arms the shutdown timer. Runs after health.sh in the same hook, so only a deploy that
# actually came up starts the clock. Every deploy re-arms it, which means a push during a
# session extends the hour instead of inheriting the previous deploy's remaining minutes.
# The schedule deletes itself once it fires.
set -euo pipefail

cd /opt/bibexpo
set -a
# shellcheck disable=SC1091
. deploy/image.env
set +a

MINUTES="${SLEEP_AFTER_MINUTES:-60}"
PROJECT="${SLEEP_PROJECT:-bibexpo-sleep}"
NAME="${SLEEP_SCHEDULE_NAME:-bibexpo-sleep-timer}"
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
ROLE="arn:aws:iam::${ACCOUNT}:role/${SCHEDULER_ROLE_NAME:-bibexpo-scheduler-role}"
WHEN=$(date -u -d "+${MINUTES} minutes" +%Y-%m-%dT%H:%M:%S)

# The universal SDK target takes the API's own field names, so ProjectName is capitalised.
TARGET=$(printf '{"Arn":"arn:aws:scheduler:::aws-sdk:codebuild:startBuild","RoleArn":"%s","Input":"{\\"ProjectName\\":\\"%s\\"}"}' \
  "$ROLE" "$PROJECT")

# The schedule is gone after it fires, so update first and fall back to create.
aws scheduler update-schedule --name "$NAME" \
  --schedule-expression "at($WHEN)" --schedule-expression-timezone UTC \
  --flexible-time-window '{"Mode":"OFF"}' --action-after-completion DELETE \
  --target "$TARGET" >/dev/null 2>&1 \
|| aws scheduler create-schedule --name "$NAME" \
  --schedule-expression "at($WHEN)" --schedule-expression-timezone UTC \
  --flexible-time-window '{"Mode":"OFF"}' --action-after-completion DELETE \
  --target "$TARGET" >/dev/null

echo "shutdown armed for ${WHEN}Z (in ${MINUTES} minutes)"
