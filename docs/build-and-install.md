# Build and install the recorded personal lab

Use this with [runbook.md](runbook.md). Commands in this file run **inside the Linux EC2 Session Manager terminal**, except the explicitly labeled Mac mini checkout. The source files, downloads, Linux image build, registration and pipeline execution were exercised in this lab. The fresh-directory assembly below uses the subsequently verified private Git key route instead of the initial one-time source-bundle transfer. That full fresh-host sequence has not been replayed end to end.

Do not run Linux bootstrap commands on the Mac. Reuse existing resources when resuming. New builds may have different hashes; an old image hash is an evidence record, not a promise of reproducible bytes.

## 1. Prepare the repository on the Mac mini

The current checkout is the task's `lab/` directory. For another local working directory, use the authenticated personal GitHub account:

```bash
gh repo clone Korrojo/mongodb-liquibase-harness-lab
git -C mongodb-liquibase-harness-lab switch setup/lab-foundation
git -C mongodb-liquibase-harness-lab status --short --branch
```

Review the files before publication. Use a setup branch. The synced `sources/` and the original iCloud handoff remain read-only reference material. Keep populated environment files, private keys, token values, binaries and raw credential-bearing logs out of Git. GitHub login/MFA belongs in the browser when required; never copy the mini's personal GitHub token to EC2.

## 2. AWS Console configuration

Open the intended learning account in `us-east-1`. The exact deployed identifiers and earlier console evidence are in [resource-inventory.md](resource-inventory.md) and [aws-console-checkpoint.md](aws-console-checkpoint.md).

1. IAM → Roles: reuse `mongodb-lab-ec2-ssm`. On a fresh account, create an EC2 service role with only `AmazonSSMManagedInstanceCore`, and attach the instance profile to the host. The administrator starting browser SSM sessions separately needs permission to start them.
2. Verify a public subnet with an attached internet gateway, default route, DNS resolution/hostnames and outbound replies permitted. The recorded default VPC and subnet were inspected before launch.
3. EC2 → Launch instance, using the reviewed values:

| Field | Recorded selection |
|---|---|
| Name | `mongodb-lab-delegate` |
| AMI | Amazon Linux 2023 x86_64, `ami-081b0a6eac00b4f53`, release `2023.12.20260831.0` |
| Instance | One `m7i-flex.large`, 2 vCPU / 8 GiB |
| Key pair | Proceed without a key pair; use SSM |
| Network | Recorded public subnet, automatic public IPv4 |
| Security group | Lab-only group; **zero inbound rules**; required outbound access retained |
| Disk | 30 GiB encrypted gp3, 3000 IOPS / 125 MiB/s, delete on termination |
| IAM profile | `mongodb-lab-ec2-ssm` |
| Metadata | IMDSv2 required, hop limit 1 |
| Instance-initiated shutdown | **Stop** |
| User data | Full contents of `infra/aws/user-data.sh` |

Recheck AMI availability, account policy and prices on a future installation. Do not assume historical credits offset costs. The existing approval covered the current personal lab and $20/month before credits.

4. Open the instance → Connect → Session Manager. Run:

```bash
sudo cloud-init status --wait
sudo systemctl is-active amazon-ssm-agent mongodb-lab-autostop.timer
sudo systemctl list-timers --all mongodb-lab-autostop.timer --no-pager
sudo systemctl cat mongodb-lab-autostop.timer mongodb-lab-autostop.service
```

Require cloud-init completion, the timer active, a deadline about two hours after its activation, and a service that calls poweroff with EC2 configured to Stop. The timer is boot-armed, not an application job queue.

5. Test shutdown **while the new host is idle**, before migrations. Paste/run `infra/aws/test-autostop.sh` with root permissions. It installs only a `/run` override for 45 seconds. Wait for EC2 Stopped, start the same instance, reconnect and verify the two-hour timer has returned. `/run` resets on boot. This test really stopped and restarted the recorded instance.
6. Install the host tools:

```bash
sudo dnf -y install docker git
sudo systemctl enable --now docker
sudo docker version
```

The observed host versions were Docker 25.0.16 and Git 2.50.1. The build script installs the host Corretto Java 17 development package.

## 3. Create a repository-specific read key

On a fresh host, prepare a dedicated directory and key. Do not overwrite an existing key:

```bash
sudo bash -c 'set -e; umask 077; install -d -m 0750 -o 1001 -g 0 /etc/mongodb-lab/git; test ! -e /etc/mongodb-lab/git/id_ed25519; ssh-keygen -t ed25519 -N "" -C mongodb-lab-ec2-readonly -f /etc/mongodb-lab/git/id_ed25519; chown 1001:0 /etc/mongodb-lab/git/id_ed25519; chmod 0600 /etc/mongodb-lab/git/id_ed25519'
sudo cat /etc/mongodb-lab/git/id_ed25519.pub
sudo ssh-keygen -lf /etc/mongodb-lab/git/id_ed25519.pub
```

Only the **public** `.pub` value is copied. GitHub → repository → Settings → Deploy keys → Add deploy key, title `mongodb-lab-ec2-readonly`, paste the public key, leave **Allow write access unchecked**, Add key. Verify Read-only and the matching fingerprint. For this lab it was `SHA256:HGZUrEyXCJo3x7VOxzcXCA/NkSllq0/Tzwh2WuwqEzc`. A newly generated key will have a different fingerprint.

Create `known_hosts` using the current official GitHub published host key. The recorded Ed25519 entry was:

```text
github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl
```

Its expected fingerprint was `SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU`. Verify against [GitHub's published fingerprints](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints) when repeating later. Do not disable host checking or trust an unverified scan after a mismatch.

```bash
sudo tee /etc/mongodb-lab/git/known_hosts >/dev/null <<'HOSTKEY'
github.com ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl
HOSTKEY
sudo chown 1001:0 /etc/mongodb-lab/git/known_hosts
sudo chmod 0644 /etc/mongodb-lab/git/known_hosts
sudo ssh-keygen -lf /etc/mongodb-lab/git/known_hosts
```

## 4. Fetch the exact source revision on EC2

Open a root shell in the SSM terminal for this section. This avoids the common error where an unprivileged outer shell tries to write a root-owned log before `sudo` starts.

```bash
sudo -i
set -e
umask 027
export GIT_TERMINAL_PROMPT=0
export GIT_SSH_COMMAND='ssh -i /etc/mongodb-lab/git/id_ed25519 -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=yes -o UserKnownHostsFile=/etc/mongodb-lab/git/known_hosts -o ConnectTimeout=15'
install -d -m 0755 /opt/mongodb-lab
```

Only if `/opt/mongodb-lab/repo` is absent:

```bash
git clone --no-checkout git@github.com:Korrojo/mongodb-liquibase-harness-lab.git /opt/mongodb-lab/repo
```

For each reviewed revision, use the following pattern. The sample SHA is the tested runtime build snapshot:

```bash
cd /opt/mongodb-lab/repo
LAB_REVISION=4f5706401a58ae5bab9c6bb8bcb120ad08ee959f
git fetch --depth 1 origin "$LAB_REVISION"
git checkout --detach "$LAB_REVISION"
test "$(git rev-parse HEAD)" = "$LAB_REVISION"
```

Do not change a host checkout with local modifications without inspecting them. The actual Harness migration checkout is separate: each run creates its own private directory and fetches its exact input SHA as UID 1001.

## 5. Build both runtime images

Remain in the root SSM shell. Use the **001-only build revision** above. `RuntimeProbe` intentionally expects one changeset in the master at build time; the current three-change master is not that build snapshot.

Create a fresh base build directory with credential-free source inputs:

```bash
test ! -e /opt/mongodb-lab/build-001
install -d -m 0755 /opt/mongodb-lab/build-001/inputs
git -C /opt/mongodb-lab/repo archive "$LAB_REVISION" | tar -x -C /opt/mongodb-lab/build-001/inputs
cd /opt/mongodb-lab/build-001
bash inputs/infra/delegate/prepare-on-ec2.sh > build.log 2>&1
tail -n 15 build.log
```

Require zero exit and `CUSTOM_RUNTIME_BUILD_PASS`. The script downloads Maven 3.9.16 with a pinned SHA-512, checks out the pinned public extension source, applies patch 001, runs focused tests, resolves and hash-checks 16 runtime libraries, verifies the mongosh 2.10.0 archive SHA-256, builds `lab.1`, and checks versions as UID 1001. The Dockerfile pins the delegate base digest. No credentials are build inputs. `lab.1` is the historical driver-backed baseline; it is not approved for native credential use.

Build the separate repaired native candidate:

```bash
cd /opt/mongodb-lab/repo
bash infra/delegate/build-native-candidate.sh > /opt/mongodb-lab/native-candidate-build.log 2>&1
tail -n 10 /opt/mongodb-lab/native-candidate-build.log
cat /opt/mongodb-lab/build-native-001/validation/runtime.log
cat /opt/mongodb-lab/build-native-001/validation/native.log
```

Require zero exit, `NATIVE_CANDIDATE_BUILD_PASS`, the offline pass and `NATIVE_MONGOSH_LOGS_CLEAN`. This source script applies both patches and runs 62 focused tests (observed: 61 passed, one existing DNS-related skip), then isolated candidate-container checks with real mongosh and synthetic credentials. It builds from `/opt/mongodb-lab/build-001` tools/cache into a fresh `/opt/mongodb-lab/build-native-001`. Do not delete a previous failed build blindly; inspect its logs and use a reviewed clean retry location if needed.

Record image and extension hashes:

```bash
docker image inspect mongodb-lab-delegate:lab.1 --format '{{.Id}}'
docker image inspect mongodb-lab-delegate:lab.native-candidate --format '{{.Id}}'
sha256sum /opt/mongodb-lab/build-native-001/extension.jar
```

The observed native image and JAR hashes are in [versions.md](versions.md). The build test count does not imply live Atlas has authenticated; sections 7–8 of the main runbook complete that evidence.

## 6. Register the delegate without putting its token in the image

Remain in the root SSM shell. Fetch and detach the completed helper revision using section 4's pattern:

```bash
cd /opt/mongodb-lab/repo
LAB_REVISION=e9a7bd6ba2d2f19f84d6e42a91d71ae6e6faf6e4
git fetch --depth 1 origin "$LAB_REVISION"
git checkout --detach "$LAB_REVISION"
test "$(git rev-parse HEAD)" = "$LAB_REVISION"
```

Harness → Project Settings → Delegates → New Delegate → Docker: use the installer offered by the account to obtain its registration token. Keep the token value private. The prepared script already supplies the recorded account ID, manager URL, delegate name/type and selector. Review these against the actual installer; do not execute a different unreviewed installer or enable its upgrader blindly.

On a fresh host, the person entering the token can use a hidden terminal prompt. Run this interactively, enter only the token, then press Return:

```bash
set +x
umask 077
test ! -e /etc/mongodb-lab/delegate.env
read -r -s -p 'Harness delegate token: ' LAB_DELEGATE_TOKEN
printf '\n'
test -n "$LAB_DELEGATE_TOKEN"
printf 'DELEGATE_TOKEN=%s\n' "$LAB_DELEGATE_TOKEN" > /etc/mongodb-lab/delegate.env
unset LAB_DELEGATE_TOKEN
chmod 0600 /etc/mongodb-lab/delegate.env
stat -c '%a:%u' /etc/mongodb-lab/delegate.env
```

Require `600:0`. Reuse the existing protected file when resuming. Do not print it, dump container environment variables or include it in a build context. Registration token transfer and the Git key grant were explicitly approved for the current lab; later account access needs its own authorized context.

Register the baseline with the approved read-only key already available:

```bash
LAB_GIT_DIRECTORY=/etc/mongodb-lab/git bash infra/delegate/register-delegate.sh
docker inspect mongodb-lab --format 'STATE={{.State.Status}} HEALTH={{.State.Health.Status}} IMAGE={{.Image}}'
docker inspect mongodb-lab --format 'MOUNTS={{range .Mounts}}{{.Destination}}:rw={{.RW}} {{end}}'
```

This fresh-install sequence attaches the already approved key on first registration. In the actual initial installation the key was granted later and `attach-repository-access.sh` preserved the old container as `mongodb-lab-before-git` while attaching the same read-only mount. Do not run that replacement script unnecessarily if the mount is already correct.

Require healthy and Harness Connected. The script uses UID 1001, 1 CPU, 4 GiB, restart `unless-stopped`, 120-second stop timeout, JSON logs bounded to 10 MiB × 3, no host ports, no privileged mode, no Docker socket and no automatic upgrader. The delegate's outbound connection supplies tasks.

Install driver/first-migration helpers using the host JDK:

```bash
bash infra/delegate/install-connectivity-probe.sh
bash infra/delegate/install-first-migration-probe.sh
```

Require each installation marker and zero exit. Save/run the runtime-check and Atlas connectivity pipelines from the main runbook. The first migration at revision `19f335da99e7079961a07f623db09acac5ec7a45` can now establish phase 1 using the driver-backed baseline. Stop if authentication fails.

## 7. Promote the tested native candidate

There must be no active Harness execution. The promotion script checks the shared target lock and active timer, verifies the current baseline image, preserves the old container stopped as `mongodb-lab-before-native`, and creates the new one with the same hostname, token, resource limits and read-only key mount.

**Fresh-build hash checkpoint:** `promote-native-candidate.sh` and `install-native-atlas-probe.sh` intentionally contain the image ID tested in this installation. If a fresh build produced a different image ID, the scripts refuse it. After reviewing that fresh build's complete acceptance logs and recording its image/JAR hashes, update only those two expected-image guards to that reviewed image ID in the local host checkout and inspect the diff. Do not remove the checks or replace them with an unreviewed arbitrary tag. The current recorded host needs no such edit.

```bash
cd /opt/mongodb-lab/repo
bash infra/delegate/promote-native-candidate.sh > /tmp/mongodb-native-promotion.log 2>&1
cat /tmp/mongodb-native-promotion.log
```

Require `NATIVE_CONTAINER_CREATED`, then separately verify healthy and Harness Connected. The script restores the old container if creation fails; creation alone does not prove registration/health. Do not start the stopped backup at the same time as the active delegate.

Reinstall all helpers in the replacement container:

```bash
bash infra/delegate/install-connectivity-probe.sh
bash infra/delegate/install-first-migration-probe.sh
bash infra/delegate/install-native-atlas-probe.sh
bash infra/delegate/install-exercise-probe.sh
```

The native installer copies the anonymous inner classes as well as the main class. The container has a JRE, so compilation happens on the host with `javac --release 17` against the pinned libraries. These classes survive stopping/starting this same container, but must be installed again after replacing it.

Run `mongodb-lab-native-connectivity` and require its live credential/cleanup PASS markers. Then complete the index, native fixture and remaining exercises in the main runbook's exact order. The host checkout may contain all three changelogs: each pipeline fetches its independently specified historical SHA, so the migration phases are still controlled.

## 8. Upgrade and recovery boundaries

The images are stored on the retained EBS volume, not published to a registry. Preserve the build recipe, source patches, hashes and acceptance evidence. For a future delegate base/Java/Liquibase/extension/mongosh update, build a distinct candidate, review compatibility and credential behavior, repeat isolated and live acceptance, and only then replace the active container during an idle window. Do not let an automatic delegate upgrade replace this custom runtime and discard the tools. [Harness custom-image guidance](https://developer.harness.io/docs/platform/delegates/install-delegates/build-custom-delegate-images-with-third-party-tools/).

Normal daily restart needs no rebuild or new key. Use the main runbook's start/verify/stop sequence. If a candidate fails, preserve its logs/container for diagnosis, confirm no migration is active, and review restoration of the stopped prior image. Never run two containers claiming the same delegate identity simultaneously. A source/metadata inconsistency is investigated before any database repair or cleanup.
