#!/usr/bin/env bash
set -Eeuo pipefail
test "$(id -u)" = 0
systemctl is-active --quiet mongodb-lab-autostop.timer
install -d -m 0755 /opt/mongodb-lab/probes
javac --release 17 -cp '/opt/mongodb-lab/build-001/runtime-libs/*' -d /opt/mongodb-lab/probes scripts/LabExerciseProbe.java
chmod 0644 /opt/mongodb-lab/probes/LabExerciseProbe.class
docker cp /opt/mongodb-lab/probes/LabExerciseProbe.class mongodb-lab:/opt/mongodb-lab/probes/
docker exec -u 1001 mongodb-lab test -r /opt/mongodb-lab/probes/LabExerciseProbe.class
printf 'EXERCISE_PROBE_INSTALLED\n'
