#!/usr/bin/env bash
# Installed and checksum-pinned on the delegate; never loaded from event-controlled code.
set +x
set -Eeuo pipefail
umask 077
test "$(id -u)" = 1001
[[ "${LAB_COMMIT:?}" =~ ^[0-9a-f]{40}$ ]]
test -n "${ATLAS_PASSWORD:?}"
exec 8>/opt/mongodb-lab/locks/lifecycle.lock
flock -s -w 10 8
test ! -e /opt/mongodb-lab/locks/draining || { printf 'LAB_DRAINING\n'; exit 75; }
exec 9>/opt/mongodb-lab/locks/cluster0-liquibase_lab.lock
flock -w 180 9 || { printf 'LAB_BUSY\n'; exit 75; }
export LAB_RUN_DIR
LAB_RUN_DIR=$(mktemp -d /opt/mongodb-lab/work/migration.XXXXXX)
cleanup() {
  local result=$?
  trap - EXIT
  [[ "$LAB_RUN_DIR" == /opt/mongodb-lab/work/migration.* ]] && rm -rf -- "$LAB_RUN_DIR"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
lab_cp='/opt/mongodb-lab/lib/*:/opt/mongodb-lab/probes'
java -cp "$lab_cp" LabDeployGuard
mkdir "$LAB_RUN_DIR/repo"
cd "$LAB_RUN_DIR/repo"
export GIT_TERMINAL_PROMPT=0
git init --quiet
git -c core.hooksPath=/dev/null fetch --quiet --depth=1 \
  https://github.com/Korrojo/mongodb-liquibase-harness-lab.git "$LAB_COMMIT"
git -c core.hooksPath=/dev/null checkout --quiet --detach FETCH_HEAD
test "$(git rev-parse HEAD)" = "$LAB_COMMIT"
# Recheck after checkout and while holding the target lock; never deploy stale queued events.
java -cp "$lab_cp" LabDeployGuard
unset LAB_GITHUB_TOKEN
export LIQUIBASE_COMMAND_URL='mongodb+srv://cluster0.okiw7qi.mongodb.net/liquibase_lab?authSource=admin&retryWrites=true&w=majority&serverSelectionTimeoutMS=15000&connectTimeoutMS=10000'
export LIQUIBASE_COMMAND_USERNAME=liquibase_lab_user
export LIQUIBASE_COMMAND_PASSWORD="$ATLAS_PASSWORD"
export LIQUIBASE_MONGODB_SUPPORTS_VALIDATOR=false
export LIQUIBASE_MONGODB_MONGOSH_TEMP_DIRECTORY="$LAB_RUN_DIR/native-temp"
mkdir "$LIQUIBASE_MONGODB_MONGOSH_TEMP_DIRECTORY"
run_liquibase() {
  local label=$1 result=0
  shift
  liquibase --search-path=. --changelog-file=changelog/db.changelog-master.yaml "$@" > "$LAB_RUN_DIR/$label.log" 2>&1 || result=$?
  java -cp "$lab_cp" LabFirstMigrationProbe sanitize "$LAB_RUN_DIR/$label.log"
  printf 'LIQUIBASE_STEP=%s EXIT=%s\n' "$label" "$result"
  return "$result"
}
java -cp "$lab_cp" LabDeploymentProbe before
run_liquibase validate validate
run_liquibase update update
lab_after=$(java -cp "$lab_cp" LabDeploymentProbe after)
printf '%s\n' "$lab_after"
run_liquibase repeat-update update
test "$lab_after" = "$(java -cp "$lab_cp" LabDeploymentProbe after)"
printf 'MERGED_DEPLOYMENT_PASS COMMIT=%s; repeat is a no-op; lock released on exit\n' "$LAB_COMMIT"
