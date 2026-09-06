#!/usr/bin/env bash
# Run as root from the reviewed lab checkout on the existing EC2 host.
set -Eeuo pipefail
test "$(id -u)" = 0
test -f scripts/AtlasConnectivityProbe.java
systemctl is-active --quiet mongodb-lab-autostop.timer
test "$(docker inspect --format '{{.State.Running}}' mongodb-lab)" = true
install -d -m 0755 /opt/mongodb-lab/probes
javac --release 17 -cp '/opt/mongodb-lab/build-001/runtime-libs/*' \
  -d /opt/mongodb-lab/probes scripts/AtlasConnectivityProbe.java
chmod 0644 /opt/mongodb-lab/probes/AtlasConnectivityProbe.class
docker exec -u 0 mongodb-lab mkdir -p /opt/mongodb-lab/probes
docker cp /opt/mongodb-lab/probes/AtlasConnectivityProbe.class mongodb-lab:/opt/mongodb-lab/probes/
docker exec -u 1001 mongodb-lab test -r /opt/mongodb-lab/probes/AtlasConnectivityProbe.class
printf 'CONNECTIVITY_PROBE_INSTALLED\n'
