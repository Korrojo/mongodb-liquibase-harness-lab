# AWS Console preparation and replication checkpoint

> Historical checkpoint: this records an earlier phase, including its then-pending work and inputs. It is retained for diagnosis, not as the current run sequence. Atlas authentication, all three migration lessons, scoped rollbacks, invalid-YAML rejection, overlap exclusion and post-restart verification subsequently passed. Use [the current runbook](runbook.md) and [acceptance ledger](validation-results.md). EC2 was left Stopped.

September 6, 2026. **EC2 launched; Session Manager and timed stop/restart/rearm verified.** The user explicitly approved the role/assignment and then the one-server launch with a $20 monthly AWS lab limit before credits.

## Recovering the side browser

In this execution, selecting AWS and Atlas initially failed because a managed browser policy could not be verified. Resetting the computer-use session and reopening the task did not help. The user fully quit/reopened the Mac mini's ChatGPT app and restored the tabs; subsequent normal access checks succeeded for AWS, Atlas, and Harness. This establishes a successful recovery procedure for this incident, not a diagnosis of its underlying cause. Return to this same task after restarting, and discover fresh tab identifiers.

## 1. Inspect before creating anything

1. In AWS Console Home, confirm Learning-account `224772450208`, user `lab-admin`, and N. Virginia (`us-east-1`). Record credits and current-month cost. This session showed $100 and $0 respectively; balances can update later.
2. In EC2, open **Instances**, then **Volumes**. The unfiltered lists were empty in this region.
3. Open **Security Groups**. Only `default` existed. Do not attach that group to the lab merely because it is available.
4. In IAM → **Roles**, check whether `mongodb-lab-ec2-ssm` exists. It did not; only three unrelated service-linked roles were present.
5. In VPC, inspect the selected VPC and subnet. The recorded subnet uses the main route table with an active internet-gateway route. DNS resolution and DNS hostnames are enabled. See [resource inventory](resource-inventory.md) for the verified IDs.

## 2. Prepare the management role

These steps were performed through creation and readback:

1. IAM → **Roles → Create role**.
2. Choose **AWS service**, service **EC2**, and the ordinary **EC2** use case.
3. Choose **Next**. Keep **Use existing policy**. Search for the exact name `AmazonSSMManagedInstanceCore` and select only that policy.
4. Choose **Next**. Set role name `mongodb-lab-ec2-ssm` and description `Session Manager access for the MongoDB Liquibase Harness lab EC2 instance.`
5. Review trust: principal `ec2.amazonaws.com`, action `sts:AssumeRole`, effect `Allow`. Review permissions: only `AmazonSSMManagedInstanceCore`. No CloudWatch agent policy or administrator policy is selected.
6. After the user approved creating and assigning this role, click **Create role**. The console confirmed success. **View role** showed role ARN `arn:aws:iam::224772450208:role/mongodb-lab-ec2-ssm`, instance-profile ARN `arn:aws:iam::224772450208:instance-profile/mongodb-lab-ec2-ssm`, and exactly one attached policy, `AmazonSSMManagedInstanceCore`. Approval is already satisfied; do not request it again for this scope.

This AWS-managed policy supports the agent's core Systems Manager communication. It also includes SSM parameter-read permissions with a wildcard resource; it is not literally an SSM-connection-only custom policy. The role does not grant the signed-in person's `StartSession` permissions. [AWS policy definition](https://docs.aws.amazon.com/aws-managed-policy/latest/reference/AmazonSSMManagedInstanceCore.html).

## 3. Prepare the EC2 launch form

| Field | Prepared value |
|---|---|
| Count/name | One / `mongodb-lab-delegate` |
| Image | Amazon Linux 2023, `2023.12.20260831.0`, kernel 6.18, x86_64; `ami-081b0a6eac00b4f53` |
| Instance | `m7i-flex.large`, 2 vCPU, 8 GiB; console Linux base price $0.09576/hour |
| Key pair | Proceed without a key pair |
| VPC/subnet | Recorded default VPC; explicit subnet `subnet-09c6def06208373b3` in `us-east-1a` |
| Public IPv4 | Enable |
| New security group | `mongodb-lab-delegate-sg`; description identifies the lab and Session Manager administration |
| Inbound rules | None; unchecked the launch wizard's default **Allow SSH traffic from** |
| Root disk | `/dev/xvda`, 30 GiB, gp3, 3000 IOPS, 125 MiB/s; initialization rate left unset |
| Encryption | Encrypted; `(default) aws/ebs`, key alias `alias/aws/ebs` |
| Delete disk on termination | Yes |
| Instance profile | `arn:aws:iam::224772450208:instance-profile/mongodb-lab-ec2-ssm` selected and read back |
| Shutdown behavior | Stop |
| Metadata | Enabled; V2 only; hop limit 1 |
| User data | Contents of [user-data.sh](../infra/aws/user-data.sh), pasted as plain text; already-base64 checkbox unchecked |

Use **Edit** in Network settings to name the security group and choose the subnet. Use **Advanced** in storage, expand **Volume 1**, and choose encryption and the default EBS key. Open **Advanced details** for instance profile, shutdown behavior, metadata, and user data.

The small `t3.micro` default is unsuitable for this combined runtime. This account's picker allowed `m7i-flex.large`; `t3.medium` and `t3.large` were disabled. The `c7i-flex.large` option had only 4 GiB for a small compute-price reduction; the prepared choice retains the handoff's 8 GiB design.

**Performed:** re-read the final form under the user’s approval and submit once. AWS confirmed `i-0635332c43aa733a5`, launched at 17:28:26 UTC. Lab security group is `sg-0b2f11fa130e4facc`; encrypted 30 GiB root is `vol-061b8b79de6fdaa6a`. The plan file is a review manifest, not an AWS API payload.

## 4. Automatic stop safeguard and required tests

The deployed user data installs `mongodb-lab-autostop.timer` and its service, enables the timer at boot, then enables SSM Agent. `OnActiveSec=2h` measures from timer activation; restarting the timer starts another two-hour interval. It does not measure whether a person is currently using the server. [systemd timer documentation source](https://github.com/systemd/systemd/blob/main/man/systemd.timer.xml).

The service requests an operating-system poweroff. With EC2 **Shutdown behavior = Stop**, an EBS-backed instance stops and retains its disk. The disk can still accrue charges. [AWS shutdown behavior](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_ChangingInstanceInitiatedShutdownBehavior.html).

After launch, through **Connect → Session Manager**, run:

```bash
sudo cloud-init status --wait
sudo systemctl is-active amazon-ssm-agent mongodb-lab-autostop.timer
sudo systemctl is-enabled mongodb-lab-autostop.timer
sudo systemctl list-timers --all mongodb-lab-autostop.timer
sudo systemctl cat mongodb-lab-autostop.timer mongodb-lab-autostop.service
```

Verify the actual units and deadline before lengthy installation. Confirm cloud-init has completed successfully; inspect its bounded logs if it failed. Do not leave a running instance unattended when the safeguard is missing or unverified.

To deliberately extend an active lab session, before the deadline and with sufficient budget:

```bash
sudo systemctl restart mongodb-lab-autostop.timer
sudo systemctl list-timers --all mongodb-lab-autostop.timer
```

**Performed acceptance test:** use [test-autostop.sh](../infra/aws/test-autostop.sh) as root through Session Manager on the idle server. It places a 45-second override under `/run`, reloads systemd, and restarts the actual timer. The timer's 17:31:57 UTC deadline elapsed and EC2 reported **Stopped**. No Console Stop action was used for this test. Start the same instance from **Instance state → Start instance**, reconnect through **Connect → In web browser → SSM Session Manager → Connect**, then run:

```bash
sudo systemctl is-active amazon-ssm-agent mongodb-lab-autostop.timer
sudo systemctl is-enabled mongodb-lab-autostop.timer
sudo systemctl show mongodb-lab-autostop.timer -p DropInPaths -p TimersMonotonic --no-pager
sudo systemctl list-timers --all mongodb-lab-autostop.timer --no-pager
```

Observed: both services active, timer enabled, `OnActiveUSec=2h`, empty `DropInPaths`, next deadline **19:32:59 UTC**. The temporary `/run` override disappeared at boot and remote access recovered. This proves the expiry/stop/rearm path with a short interval; a full two-hour soak has not been run. Check remaining time before starting any migration; the timer can interrupt active work. Public IPv4 is released on stop, so re-read it after restart.

## 5. Harness and Atlas observations

- The saved runtime-check pipeline still showed Validated. It has not been run by this task.
- Project Settings → Delegates showed no project delegates. **Install a Delegate → Docker** offered `us-docker.pkg.dev/gar-prod-setup/harness-public/harness/delegate:26.08.89804`. Record a verified image digest and build the custom runtime before registering it. Do not copy a real delegate token into a tracked script, image layer, or this runbook.
- Atlas `Cluster0` is FREE, MongoDB 8.0.32, in AWS N. Virginia. `liquibase_lab` was absent. The only current database user is a broad administrator; create a separate lab user after the required access confirmation and credential-entry handoff.
- Atlas has an active `0.0.0.0/0` network rule among six entries. A new EC2 `/32` would not enforce source restriction while it remains. Review existing clients with the user before narrowing this project-wide rule. No network or database-user changes have been made.
- Runtime compatibility, safe native credential handling, GitHub publication, migration execution, rollback, and idempotency checks remain unfinished. This checkpoint does not mark the full lab validated.
