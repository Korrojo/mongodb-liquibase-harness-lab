#!/usr/bin/env bash
# Run as root on the existing EC2 delegate host. Does not stop it during install.
set -Eeuo pipefail
test "$(id -u)" = 0
test "$(uname -s)" = Linux
systemctl is-active --quiet mongodb-lab-autostop.timer
docker exec mongodb-lab test -d /opt/mongodb-lab/locks
install -d -m 0755 /usr/local/libexec
cat > /usr/local/libexec/mongodb-lab-drain-stop <<'SCRIPT'
#!/usr/bin/env bash
set -Eeuo pipefail
if docker inspect --format '{{.State.Running}}' mongodb-lab | grep -Fx true >/dev/null; then
  # Future jobs reject this flag while existing jobs retain their shared lock.
  docker exec -u 0 mongodb-lab touch /opt/mongodb-lab/locks/draining
  docker exec -u 0 mongodb-lab flock -x -w 900 /opt/mongodb-lab/locks/lifecycle.lock true
fi
logger -t mongodb-lab 'Active work drained; stopping lab host'
systemctl --no-block poweroff
SCRIPT
cat > /usr/local/libexec/mongodb-lab-ready <<'SCRIPT'
#!/usr/bin/env bash
set -Eeuo pipefail
for attempt in {1..60}; do
  if docker exec -u 0 mongodb-lab sh -c 'touch /opt/mongodb-lab/locks/lifecycle.lock && chmod 0666 /opt/mongodb-lab/locks/lifecycle.lock && rm -f /opt/mongodb-lab/locks/draining'; then
    exit 0
  fi
  sleep 2
done
exit 1
SCRIPT
chmod 0755 /usr/local/libexec/mongodb-lab-{drain-stop,ready}
cat > /etc/systemd/system/mongodb-lab-ready.service <<'UNIT'
[Unit]
Description=Allow lab jobs after delegate restart
After=docker.service
Requires=docker.service
[Service]
Type=oneshot
ExecStart=/usr/local/libexec/mongodb-lab-ready
RemainAfterExit=yes
[Install]
WantedBy=multi-user.target
UNIT
cat > /etc/systemd/system/mongodb-lab-autostop.service <<'UNIT'
[Unit]
Description=Drain active lab jobs before stopping the host
[Service]
Type=oneshot
ExecStart=/usr/local/libexec/mongodb-lab-drain-stop
TimeoutStartSec=16min
UNIT
systemctl daemon-reload
systemctl enable --now mongodb-lab-ready.service
systemctl is-active --quiet mongodb-lab-autostop.timer
printf 'SAFE_AUTOSTOP_INSTALLED: original two-hour timer retained\n'
