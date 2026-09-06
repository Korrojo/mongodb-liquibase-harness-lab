#!/bin/bash
# Run with sudo from the EC2 build directory. Uses fake credentials only.
set -Eeuo pipefail
install -d -m 0755 validation/changelog
cat > validation/driver-failure.properties <<'CONFIG'
url=mongodb://127.0.0.1:1/liquibase_lab?serverSelectionTimeoutMS=1500&connectTimeoutMS=1000
username=offline_user
password=LAB_DRIVER_FAILURE_CANARY_916
CONFIG
chmod 0600 validation/driver-failure.properties
chown 1001:0 validation/driver-failure.properties
set +e
docker run --rm --network none --entrypoint /usr/local/bin/liquibase \
    -v "$PWD/validation:/validation:ro" \
    -v "$PWD/inputs/changelog:/validation/changelog:ro" \
    mongodb-lab-delegate:lab.1 \
    --defaults-file=/validation/driver-failure.properties \
    --search-path=/validation --changelog-file=changelog/db.changelog-master.yaml \
    --log-level=info update > validation/driver-failure.log 2>&1
LAB_FAILURE_EXIT=$?
set -e
test "$LAB_FAILURE_EXIT" -ne 0
if grep -F 'LAB_DRIVER_FAILURE_CANARY_916' validation/driver-failure.log >/dev/null; then
    printf '%s\n' 'FAIL: synthetic password was found in the failure log'
    exit 1
fi
grep -E 'MongoTimeoutException|Connection refused|Timed out.*server' validation/driver-failure.log >/dev/null
printf 'DRIVER_FAILURE_REDACTION_PASS expected_nonzero_exit=%s\n' "$LAB_FAILURE_EXIT"
