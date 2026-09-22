#!/usr/bin/env bash
# Sleep CodeBuild project, fired by the one-time EventBridge schedule and by the daily safety net.
set -euo pipefail

: "${INSTANCE_ID:?}"
DB_ID="${DB_ID:-bibexpo-db}"
KEEP="${KEEP_SNAPSHOTS:-3}"

# The box goes down first so nothing writes while the final snapshot is taken.
aws ec2 stop-instances --instance-ids "$INSTANCE_ID" >/dev/null
aws ec2 wait instance-stopped --instance-ids "$INSTANCE_ID"

if aws rds describe-db-instances --db-instance-identifier "$DB_ID" >/dev/null 2>&1; then
  aws rds delete-db-instance --db-instance-identifier "$DB_ID" \
    --final-db-snapshot-identifier "$DB_ID-$(date -u +%Y%m%d-%H%M%S)" \
    --no-skip-final-snapshot >/dev/null
  aws rds wait db-instance-deleted --db-instance-identifier "$DB_ID"
fi

aws rds describe-db-snapshots --snapshot-type manual \
  --query "sort_by(DBSnapshots[?DBInstanceIdentifier=='$DB_ID'], &SnapshotCreateTime)[:-$KEEP].DBSnapshotIdentifier" \
  --output text | tr '\t' '\n' | while read -r snapshot; do
    [ -n "$snapshot" ] && aws rds delete-db-snapshot --db-snapshot-identifier "$snapshot" >/dev/null
  done
