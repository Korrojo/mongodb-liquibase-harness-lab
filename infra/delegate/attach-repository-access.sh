#!/bin/bash
# Run as root with no active Harness execution, after read-key approval.
# Preserve the original container, stopped, for a reversible configuration update.
set -Eeuo pipefail
test "$(id -u)" -eq 0
LAB_SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
LAB_BACKUP=mongodb-lab-before-git
test "$(stat -c '%a:%u' /etc/mongodb-lab/git/id_ed25519)" = '600:1001'
test -r /etc/mongodb-lab/git/known_hosts
systemctl is-active --quiet mongodb-lab-autostop.timer
test "$(docker inspect mongodb-lab --format '{{.State.Status}}')" = running
if docker inspect "$LAB_BACKUP" >/dev/null 2>&1; then
    printf '%s\n' 'The backup container already exists; inspect the prior attempt before retrying.'
    exit 1
fi
LAB_PREVIOUS_HOSTNAME=$(docker inspect mongodb-lab --format '{{.Config.Hostname}}')
test "$(docker inspect mongodb-lab --format '{{.Image}}')" = \
    "$(docker image inspect mongodb-lab-delegate:lab.1 --format '{{.Id}}')"
docker stop --time 120 mongodb-lab
docker rename mongodb-lab "$LAB_BACKUP"
if LAB_DELEGATE_HOSTNAME="$LAB_PREVIOUS_HOSTNAME" \
    LAB_GIT_DIRECTORY=/etc/mongodb-lab/git bash "$LAB_SCRIPT_DIR/register-delegate.sh"; then
    docker inspect mongodb-lab --format 'STATE={{.State.Status}} USER={{.Config.User}} MOUNTS={{range .Mounts}}{{.Destination}}:rw={{.RW}} {{end}}'
else
    if docker inspect mongodb-lab >/dev/null 2>&1; then
        docker stop --time 10 mongodb-lab || true
        docker rename mongodb-lab "mongodb-lab-git-failed-$(date +%s)"
    fi
    docker rename "$LAB_BACKUP" mongodb-lab
    docker start mongodb-lab
    printf '%s\n' 'New container creation failed; restored the original container.' >&2
    exit 1
fi
# Creation alone does not establish health. Verify Connected in Harness,
# then run scripts/verify-repository-access.sh inside the new delegate.
