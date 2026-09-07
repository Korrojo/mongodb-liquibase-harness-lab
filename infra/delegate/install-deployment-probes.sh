#!/usr/bin/env bash
set -Eeuo pipefail
test "$(id -u)" = 0
test "$(uname -s)" = Linux
systemctl is-active --quiet mongodb-lab-autostop.timer
javac --release 17 -cp '/opt/mongodb-lab/build-001/runtime-libs/*' \
  -d /opt/mongodb-lab/probes scripts/LabDeployGuard.java scripts/LabDeploymentProbe.java
for name in LabDeployGuard.class LabDeploymentProbe.class; do
  docker cp "/opt/mongodb-lab/probes/$name" "mongodb-lab:/opt/mongodb-lab/probes/$name"
  docker exec -u 0 mongodb-lab chown root:root "/opt/mongodb-lab/probes/$name"
  docker exec -u 0 mongodb-lab chmod 0644 "/opt/mongodb-lab/probes/$name"
done
docker cp scripts/run-deployment.sh mongodb-lab:/opt/mongodb-lab/probes/run-deployment.sh
docker exec -u 0 mongodb-lab chown root:root /opt/mongodb-lab/probes/run-deployment.sh
docker exec -u 0 mongodb-lab chmod 0644 /opt/mongodb-lab/probes/run-deployment.sh
docker exec -u 1001 mongodb-lab sha256sum /opt/mongodb-lab/probes/LabDeployGuard.class /opt/mongodb-lab/probes/LabDeploymentProbe.class /opt/mongodb-lab/probes/run-deployment.sh
