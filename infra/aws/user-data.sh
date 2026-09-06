#!/bin/bash
# EC2 Amazon Linux 2023 user data. Do not execute on the Mac.
# Launch settings must use instance-initiated shutdown behavior: Stop.
set -Eeuo pipefail
umask 027

cat > /etc/systemd/system/mongodb-lab-autostop.service <<'UNIT'
[Unit]
Description=Stop the MongoDB lab host when its session deadline expires

[Service]
Type=oneshot
ExecStart=/usr/bin/systemctl --no-block poweroff
UNIT

cat > /etc/systemd/system/mongodb-lab-autostop.timer <<'UNIT'
[Unit]
Description=Limit each MongoDB lab host session to two hours

[Timer]
OnActiveSec=2h
AccuracySec=1s
Unit=mongodb-lab-autostop.service

[Install]
WantedBy=timers.target
UNIT

systemctl daemon-reload
systemctl enable --now mongodb-lab-autostop.timer
systemctl is-active --quiet mongodb-lab-autostop.timer
systemctl enable --now amazon-ssm-agent
printf '%s\n' 'Lab bootstrap complete. Automatic stop timer is active.'
