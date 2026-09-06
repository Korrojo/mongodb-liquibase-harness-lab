#!/usr/bin/env bash
# Run only after verifying no Harness execution is active. Uses existing approved access.
set -Eeuo pipefail
test "$(id -u)" = 0
LAB_SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
LAB_BACKUP=mongodb-lab-before-native
LAB_EXPECTED='sha256:dbbe662baa881398d88262981262c74f325e5bb5bc5b8cf911e8ad3781bfb663'
test "$(docker image inspect mongodb-lab-delegate:lab.native-candidate --format '{{.Id}}')" = "$LAB_EXPECTED"
test "$(docker inspect mongodb-lab --format '{{.State.Status}}')" = running
test "$(docker inspect mongodb-lab --format '{{.Image}}')" = \
    "$(docker image inspect mongodb-lab-delegate:lab.1 --format '{{.Id}}')"
systemctl is-active --quiet mongodb-lab-autostop.timer
if docker inspect "$LAB_BACKUP" >/dev/null 2>&1; then
    printf 'Native backup exists; inspect prior attempt before retrying\n' >&2
    exit 1
fi
docker exec -u 1001 mongodb-lab flock -n /opt/mongodb-lab/locks/cluster0-liquibase_lab.lock true
LAB_PREVIOUS_HOSTNAME=$(docker inspect mongodb-lab --format '{{.Config.Hostname}}')
docker stop --time 120 mongodb-lab
docker rename mongodb-lab "$LAB_BACKUP"
if LAB_DELEGATE_HOSTNAME="$LAB_PREVIOUS_HOSTNAME" LAB_GIT_DIRECTORY=/etc/mongodb-lab/git \
    LAB_IMAGE_TAG=mongodb-lab-delegate:lab.native-candidate bash "$LAB_SCRIPT_DIR/register-delegate.sh"; then
    docker inspect mongodb-lab --format 'STATE={{.State.Status}} IMAGE={{.Image}} USER={{.Config.User}}'
else
    if docker inspect mongodb-lab >/dev/null 2>&1; then
        docker stop --time 10 mongodb-lab || true
        docker rename mongodb-lab "mongodb-lab-native-failed-$(date +%s)"
    fi
    docker rename "$LAB_BACKUP" mongodb-lab
    docker start mongodb-lab
    printf 'Native container creation failed; restored original container\n' >&2
    exit 1
fi
printf 'NATIVE_CONTAINER_CREATED: reinstall probes and verify Healthy/Connected before any native run\n'
