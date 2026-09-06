#!/bin/bash
# Run only through Session Manager on the idle EC2 lab host.
# This will STOP the instance after 45 seconds. /run resets at boot.
set -Eeuo pipefail
test "$(uname -s)" = Linux
systemctl is-active --quiet mongodb-lab-autostop.timer
test -f /etc/systemd/system/mongodb-lab-autostop.timer
install -d -m 0755 /run/systemd/system/mongodb-lab-autostop.timer.d
cat > /run/systemd/system/mongodb-lab-autostop.timer.d/10-acceptance.conf <<'UNIT'
[Timer]
OnActiveSec=
OnActiveSec=45s
UNIT
systemctl daemon-reload
systemctl restart mongodb-lab-autostop.timer
systemctl list-timers --all mongodb-lab-autostop.timer --no-pager
printf '%s\n' 'Expect EC2 Stopped shortly. Start the same instance and verify the two-hour timer returns.'
