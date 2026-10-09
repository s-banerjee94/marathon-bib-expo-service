#!/usr/bin/env bash
# Wake CodeBuild project. Idempotent — safe to run when the box and the database are already up.
# Configuration comes from the project's environment variables; DB_MASTER_PASSWORD from Secrets Manager.
#
# DB_AZ is a preference, not a requirement. Same AZ as the box avoids cross-AZ traffic, but an AZ
# with no spare capacity for the instance class would otherwise fail the whole wake, so both calls
# retry without it. Cross-AZ costs $0.01/GB — pennies a month here, and far cheaper than a red
# pipeline. Keep DB_CLASS to something describe-orderable-db-instance-options actually lists for
# DB_ENGINE_VERSION: db.t4g.micro is not offered for MySQL 8.4 in ap-south-1 and placement fails.
set -euo pipefail

: "${INSTANCE_ID:?}"
DB_ID="${DB_ID:-bibexpo-db}"
DB_AZ="${DB_AZ:-}"

create_db() {
  aws rds create-db-instance \
    --db-instance-identifier "$DB_ID" \
    --engine mysql --engine-version "${DB_ENGINE_VERSION:?}" \
    --db-instance-class "${DB_CLASS:?}" \
    --allocated-storage "${DB_STORAGE:-10}" --storage-type "${DB_STORAGE_TYPE:-gp2}" \
    --master-username "${DB_MASTER_USERNAME:?}" \
    --master-user-password "${DB_MASTER_PASSWORD:?}" \
    --db-name "${DB_NAME:-marathon_bib_expo}" \
    --db-subnet-group-name "${DB_SUBNET_GROUP:?}" \
    --vpc-security-group-ids "${DB_SECURITY_GROUP:?}" \
    --no-multi-az --no-publicly-accessible --backup-retention-period 0 \
    --storage-encrypted --no-deletion-protection --no-auto-minor-version-upgrade \
    "$@" >/dev/null
}

restore_db() {
  aws rds restore-db-instance-from-db-snapshot \
    --db-instance-identifier "$DB_ID" \
    --db-snapshot-identifier "$SNAPSHOT" \
    --db-instance-class "${DB_CLASS:?}" \
    --db-subnet-group-name "${DB_SUBNET_GROUP:?}" \
    --vpc-security-group-ids "${DB_SECURITY_GROUP:?}" \
    --no-multi-az --no-publicly-accessible --no-deletion-protection \
    "$@" >/dev/null
}

if [ "$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
        --query 'Reservations[0].Instances[0].State.Name' --output text)" != running ]; then
  aws ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null
fi

if ! aws rds describe-db-instances --db-instance-identifier "$DB_ID" >/dev/null 2>&1; then
  SNAPSHOT=$(aws rds describe-db-snapshots --snapshot-type manual \
    --query "sort_by(DBSnapshots[?DBInstanceIdentifier=='$DB_ID'], &SnapshotCreateTime)[-1].DBSnapshotIdentifier" \
    --output text)

  if [ "$SNAPSHOT" = None ]; then
    # First ever wake: nothing to restore, Flyway builds the schema on an empty database.
    create_db --availability-zone "$DB_AZ" || create_db
  else
    # Same identifier every time, so the endpoint in app.env never changes.
    restore_db --availability-zone "$DB_AZ" || restore_db
  fi
fi

aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
aws rds wait db-instance-available --db-instance-identifier "$DB_ID"
