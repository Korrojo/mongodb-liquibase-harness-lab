#!/usr/bin/env bash
set -Eeuo pipefail
test "$(id -u)" = 0
test -f scripts/LabFirstMigrationProbe.java
systemctl is-active --quiet mongodb-lab-autostop.timer
install -d -m 0755 /opt/mongodb-lab/probes
javac --release 17 -cp '/opt/mongodb-lab/build-001/runtime-libs/*' \
  -d /opt/mongodb-lab/probes scripts/LabFirstMigrationProbe.java
chmod 0644 /opt/mongodb-lab/probes/LabFirstMigrationProbe.class
docker exec -u 0 mongodb-lab mkdir -p /opt/mongodb-lab/probes
docker cp /opt/mongodb-lab/probes/LabFirstMigrationProbe.class mongodb-lab:/opt/mongodb-lab/probes/
docker exec -u 1001 mongodb-lab test -r /opt/mongodb-lab/probes/LabFirstMigrationProbe.class
docker exec -u 1001 mongodb-lab bash -c 'command -v flock; command -v timeout'
printf 'FIRST_MIGRATION_PROBE_INSTALLED\n'
