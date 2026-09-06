#!/bin/bash
# Run inside the dedicated delegate; keeps a credential-free exact-commit checkout.
set -Eeuo pipefail
LAB_COMMIT=${1:?Provide the reviewed full Git commit SHA}
[[ "$LAB_COMMIT" =~ ^[0-9a-f]{40}$ ]] || { printf '%s\n' 'A full commit SHA is required' >&2; exit 1; }
test "$(id -u)" -eq 1001
test "$(stat -c '%a:%u' /opt/mongodb-lab/git/id_ed25519)" = '600:1001'
export GIT_TERMINAL_PROMPT=0
export GIT_SSH_COMMAND='ssh -i /opt/mongodb-lab/git/id_ed25519 -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=/opt/mongodb-lab/git/known_hosts -o ConnectTimeout=15'
LAB_CHECKOUT=$(mktemp -d /opt/mongodb-lab/work/repository-check.XXXXXX)
git -C "$LAB_CHECKOUT" init --quiet
git -C "$LAB_CHECKOUT" remote add origin git@github.com:Korrojo/mongodb-liquibase-harness-lab.git
git -C "$LAB_CHECKOUT" fetch --quiet --depth=1 origin "$LAB_COMMIT"
git -C "$LAB_CHECKOUT" checkout --quiet --detach FETCH_HEAD
test "$(git -C "$LAB_CHECKOUT" rev-parse HEAD)" = "$LAB_COMMIT"
test -f "$LAB_CHECKOUT/changelog/db.changelog-master.yaml"
test -f "$LAB_CHECKOUT/infra/delegate/patches/002-native-environment.patch"
printf 'REPOSITORY_CHECKOUT_PASS commit=%s directory=%s\n' "$LAB_COMMIT" "$LAB_CHECKOUT"
