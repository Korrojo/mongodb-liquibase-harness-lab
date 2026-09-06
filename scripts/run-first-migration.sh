#!/usr/bin/env bash
# Invoked from a reviewed exact checkout by one Harness step after taking the shared lock.
set +x
set -Eeuo pipefail
umask 077
test "$(id -u)" = 1001
test "${LAB_TARGET_LOCK_HELD:-}" = yes
test -n "${ATLAS_PASSWORD:-}"
test -f changelog/db.changelog-master.yaml
LAB_RUN_DIR=${LAB_RUN_DIR:?The bootstrap must supply its private run directory}
[[ "$LAB_RUN_DIR" == /opt/mongodb-lab/work/migration.* ]]
test -d "$LAB_RUN_DIR"
LAB_PROBE_CP='/opt/mongodb-lab/lib/*:/opt/mongodb-lab/probes'
java -cp "$LAB_PROBE_CP" LabFirstMigrationProbe preflight
export LIQUIBASE_COMMAND_URL='mongodb+srv://cluster0.okiw7qi.mongodb.net/liquibase_lab?authSource=admin&retryWrites=true&w=majority&serverSelectionTimeoutMS=15000&connectTimeoutMS=10000'
export LIQUIBASE_COMMAND_USERNAME=liquibase_lab_user
export LIQUIBASE_COMMAND_PASSWORD="$ATLAS_PASSWORD"
run_liquibase() {
  local label=$1
  shift
  local result=0
  liquibase --search-path=. --changelog-file=changelog/db.changelog-master.yaml "$@" \
    > "$LAB_RUN_DIR/$label.log" 2>&1 || result=$?
  java -cp "$LAB_PROBE_CP" LabFirstMigrationProbe sanitize "$LAB_RUN_DIR/$label.log"
  printf 'LIQUIBASE_STEP=%s EXIT=%s\n' "$label" "$result"
  return "$result"
}
run_liquibase validate validate
run_liquibase status-before status
run_liquibase first-update update
LAB_FIRST_STATE=$(java -cp "$LAB_PROBE_CP" LabFirstMigrationProbe verify)
printf '%s\n' "$LAB_FIRST_STATE"
run_liquibase repeat-update update
LAB_REPEAT_STATE=$(java -cp "$LAB_PROBE_CP" LabFirstMigrationProbe verify)
printf '%s\n' "$LAB_REPEAT_STATE"
test "$LAB_FIRST_STATE" = "$LAB_REPEAT_STATE"
run_liquibase status-after status
printf 'FIRST_MIGRATION_AND_REPEAT_PASS: unchanged history/index fingerprint after second update\n'
