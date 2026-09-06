#!/bin/bash
# Execute as root on EC2 after the concrete Harness registration is approved.
# The existing project installer token must be in a root-owned mode-0600 env file.
# Do not place that file in this repository, the image, or terminal output.
set -Eeuo pipefail
LAB_ENV_FILE=/etc/mongodb-lab/delegate.env
test "$(id -u)" -eq 0
test "$(stat -c '%a:%u' "$LAB_ENV_FILE")" = '600:0'
systemctl is-active --quiet mongodb-lab-autostop.timer
if docker container inspect mongodb-lab >/dev/null 2>&1; then
    printf '%s\n' 'The delegate container already exists; inspect it before changing it.'
    exit 1
fi
LAB_IMAGE_ID=$(docker image inspect mongodb-lab-delegate:lab.1 --format '{{.Id}}')
docker run -d --name mongodb-lab --restart unless-stopped \
    --cpus=1 --memory=4g --stop-timeout=120 \
    --log-driver=json-file --log-opt max-size=10m --log-opt max-file=3 \
    --env-file "$LAB_ENV_FILE" \
    -e DELEGATE_NAME=mongodb-lab -e NEXT_GEN=true -e DELEGATE_TYPE=DOCKER \
    -e ACCOUNT_ID=7WPs0XUoT4CnMpX3j28V4g -e DELEGATE_TAGS=mongodb-lab \
    -e MANAGER_HOST_AND_PORT=https://app.harness.io \
    "$LAB_IMAGE_ID"
# No Docker socket, host ports, or automatic upgrader are attached.
# Verify the delegate is Connected in the intended Harness project, then run the saved runtime pipeline.
