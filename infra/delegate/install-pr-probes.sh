#!/usr/bin/env bash
# Run on the EC2 host from an exact reviewed checkout, through Session Manager.
set -Eeuo pipefail
test "$(id -u)" = 0
test "$(uname -s)" = Linux
systemctl is-active --quiet mongodb-lab-autostop.timer
test -f scripts/LabPrPipeline.java
install -d -m 0755 /opt/mongodb-lab/probes
javac --release 17 -cp '/opt/mongodb-lab/build-001/runtime-libs/*' \
  -d /opt/mongodb-lab/probes scripts/LabChangelogCheck.java scripts/LabPrPipeline.java
for name in LabChangelogCheck LabPrPipeline; do
  chmod 0644 "/opt/mongodb-lab/probes/$name.class"
  docker cp "/opt/mongodb-lab/probes/$name.class" "mongodb-lab:/opt/mongodb-lab/probes/$name.class"
  docker exec -u 0 mongodb-lab chown root:root "/opt/mongodb-lab/probes/$name.class"
  docker exec -u 0 mongodb-lab chmod 0644 "/opt/mongodb-lab/probes/$name.class"
  docker exec -u 1001 mongodb-lab test -r "/opt/mongodb-lab/probes/$name.class"
  docker exec -u 1001 mongodb-lab test ! -w "/opt/mongodb-lab/probes/$name.class"
done
docker exec -u 1001 mongodb-lab java -cp '/opt/mongodb-lab/lib/*:/opt/mongodb-lab/probes' LabPrPipeline --self-test
docker exec -u 1001 mongodb-lab sha256sum /opt/mongodb-lab/probes/LabChangelogCheck.class /opt/mongodb-lab/probes/LabPrPipeline.class
printf 'PR_PROBES_INSTALLED\n'
