#!/usr/bin/env bash
set +x
set -Eeuo pipefail
umask 077
test "$(id -u)" = 1001
test "${LAB_TARGET_LOCK_HELD:-}" = yes
test -n "${ATLAS_PASSWORD:-}"
test -d "${LAB_RUN_DIR:?}"
[[ "$LAB_RUN_DIR" == /opt/mongodb-lab/work/migration.* ]]
case "${LAB_EXERCISE:?}" in
  index-cycle) LAB_PHASE=2 ;;
  native-cycle) LAB_PHASE=3 ;;
  validation-failure|concurrency|verify-final) LAB_PHASE=3 ;;
  *) printf 'Unrecognized lab exercise\n' >&2; exit 2 ;;
esac
LAB_PROBE_CP='/opt/mongodb-lab/lib/*:/opt/mongodb-lab/probes'
export LIQUIBASE_COMMAND_URL='mongodb+srv://cluster0.okiw7qi.mongodb.net/liquibase_lab?authSource=admin&retryWrites=true&w=majority&serverSelectionTimeoutMS=15000&connectTimeoutMS=10000'
export LIQUIBASE_COMMAND_USERNAME=liquibase_lab_user
export LIQUIBASE_COMMAND_PASSWORD="$ATLAS_PASSWORD"
export LIQUIBASE_MONGODB_SUPPORTS_VALIDATOR=false
export LIQUIBASE_MONGODB_MONGOSH_TEMP_DIRECTORY="$LAB_RUN_DIR/native-temp"
export HOME="$LAB_RUN_DIR/home"
mkdir -p "$HOME" "$LIQUIBASE_MONGODB_MONGOSH_TEMP_DIRECTORY"
run_liquibase() {
  local label=$1
  shift
  local result=0
  liquibase --search-path=. --changelog-file=changelog/db.changelog-master.yaml "$@" > "$LAB_RUN_DIR/$label.log" 2>&1 || result=$?
  java -cp "$LAB_PROBE_CP" LabFirstMigrationProbe sanitize "$LAB_RUN_DIR/$label.log"
  printf 'LIQUIBASE_STEP=%s EXIT=%s\n' "$label" "$result"
  return "$result"
}
state() { java -cp "$LAB_PROBE_CP" LabExerciseProbe "$1"; }
if [[ "$LAB_EXERCISE" == validation-failure ]]; then
  before=$(state 3)
  printf 'databaseChangeLog: [\n' > "$LAB_RUN_DIR/invalid.yaml"
  result=0
  liquibase --search-path="$LAB_RUN_DIR" --changelog-file=invalid.yaml validate > "$LAB_RUN_DIR/invalid.log" 2>&1 || result=$?
  test "$result" -ne 0
  java -cp "$LAB_PROBE_CP" LabFirstMigrationProbe sanitize "$LAB_RUN_DIR/invalid.log"
  # A missing file, authentication error or unavailable service does not prove YAML validation.
  grep -E 'while parsing|expected the node content' "$LAB_RUN_DIR/invalid.log" > /dev/null
  test "$before" = "$(state 3)"
  printf 'VALIDATION_FAILURE_BLOCKED_PASS: no update invoked; state unchanged\n'
  exit 0
fi
if [[ "$LAB_EXERCISE" == concurrency || "$LAB_EXERCISE" == verify-final ]]; then
  before=$(state 3)
  if [[ "$LAB_EXERCISE" == concurrency ]]; then
    printf 'CONCURRENCY_LOCK_HELD: holding for 45 seconds before no-op update\n'
    sleep 45
  fi
  run_liquibase final-validate validate
  run_liquibase final-update update
  test "$before" = "$(state 3)"
  printf 'FINAL_NOOP_PASS: history indexes and fixtures unchanged\n'
  exit 0
fi
state "$((LAB_PHASE - 1))"
run_liquibase validate validate
run_liquibase incremental-update update
first=$(state "$LAB_PHASE")
printf '%s\n' "$first"
run_liquibase repeat-update update
test "$first" = "$(state "$LAB_PHASE")"
printf 'INCREMENTAL_REPEAT_PASS\n'
run_liquibase scoped-rollback rollback-count --count=1
state "$((LAB_PHASE - 1))"
printf 'SCOPED_ROLLBACK_PASS\n'
run_liquibase reapply update
state "$LAB_PHASE"
if [[ "$LAB_EXERCISE" == native-cycle ]]; then state native-files; fi
printf 'EXERCISE_CYCLE_PASS exercise=%s\n' "$LAB_EXERCISE"
