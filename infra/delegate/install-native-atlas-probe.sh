#!/usr/bin/env bash
set -Eeuo pipefail
test "$(id -u)" = 0
test -f scripts/NativeAtlasProbe.java
systemctl is-active --quiet mongodb-lab-autostop.timer
test "$(docker inspect mongodb-lab --format '{{.Image}}')" = 'sha256:dbbe662baa881398d88262981262c74f325e5bb5bc5b8cf911e8ad3781bfb663'
install -d -m 0755 /opt/mongodb-lab/probes
javac --release 17 -cp '/opt/mongodb-lab/build-native-001/runtime-libs/*:/opt/mongodb-lab/build-native-001/extension.jar' \
  -d /opt/mongodb-lab/probes scripts/NativeAtlasProbe.java
docker exec -u 0 mongodb-lab mkdir -p /opt/mongodb-lab/probes
for LAB_CLASS in /opt/mongodb-lab/probes/NativeAtlasProbe*.class; do
    chmod 0644 "$LAB_CLASS"
    docker cp "$LAB_CLASS" mongodb-lab:/opt/mongodb-lab/probes/
done
printf 'NATIVE_ATLAS_PROBE_INSTALLED\n'
