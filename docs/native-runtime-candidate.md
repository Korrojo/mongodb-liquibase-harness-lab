# Native runtime repair: local and Linux candidate checkpoint

Verified September 6, 2026 on the Mac mini and EC2. The separate `lab.native-candidate` image passes Linux tests and isolated container probes. **It has not been promoted to the registered delegate or tested against Atlas.** The active delegate still uses `lab.1`. Exercise 003 remains inactive.

## Problem and resulting behavior

The pinned Harness fork passes its stored MongoDB URI to mongosh as a command argument. That URI incorporates separately supplied Liquibase credentials. Its inherited shell executor also logs child output before the subclass processes results, so adding a result-only redaction check would be too late.

The candidate patch `infra/delegate/patches/002-native-environment.patch`:

- Places the existing connection URI in the child-only `LIQUIBASE_MONGOSH_CONNECTION_URI` environment variable. No URI is added to mongosh arguments or its generated script.
- Starts mongosh with `--nodb`, `--norc`, `--quiet`, and `--file`. The generated bootstrap connects using the environment and removes that variable before the changeset script runs. Connection errors produce a generic message without the original error object.
- Captures merged child output before logging; removes the complete URI, raw password, and its URL-encoded form from captured logs and failure messages. Output over 1 MiB is drained and suppressed entirely, avoiding a truncated credential fragment.
- Creates unpredictable mode-0600 script files atomically on POSIX systems; removes them on completion, child failure, failed start, and timeout unless script retention was explicitly configured.
- Rejects extra arguments other than `--quiet` and `--norc`. Fixed temporary filenames are no longer reused. These are deliberate limitations of this lab build.

The connection method and shell flags follow [MongoDB's environment-variable scripting documentation](https://www.mongodb.com/docs/mongodb-shell/write-scripts/env-variables/) and [mongosh options](https://www.mongodb.com/docs/mongodb-shell/reference/options/). Environment variables remain accessible to sufficiently privileged processes; this is not isolation from root or another process running as the delegate user. Only reviewed lab scripts should run in this dedicated runtime. A hard process/host crash can bypass Java cleanup; do not enable script retention for normal runs.

## Actual validation

| Check | Observed result |
|---|---|
| Source | `1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021`, with patches 001 and 002 |
| Mini Java / mongosh | Java 17.0.20; existing mongosh 2.9.2 |
| Focused test/package run | 62 total, zero failures/errors, one existing skipped DNS test: 61 passed |
| New security tests | Eight passed: success, failed stdout/stderr, configured log file, oversized output, failed process start, timeout, argument rejection, real local mongosh connection failure |
| Test secrets | Synthetic only; no Atlas password used |
| Candidate JAR SHA-256 | `6fcbaa18f7433440994d452e308745a4032affce17caecac2189fa988dee14cc` |
| Offline RuntimeProbe | One provider, three change types, all three exercises parse, visible URL redaction passed |
| Linux focused suite/package | PASS: 62 tests, no failures/errors, one existing skip; real mongosh 2.10.0 security check ran |
| Candidate container as UID 1001 | PASS: offline RuntimeProbe; isolated real mongosh failure, argument/script permissions/cleanup and own-log canary checks |
| Live native authentication, fixtures and rollback | **Not tested** |

The first test iteration caught a duplicate stream-cleanup call. The final implementation uses a single dedicated output reader and passed the cleanup checks. An offline packaging attempt lacked the Maven JAR plugin; an approved dependency download resolved that and the full focused package run passed. These results do not represent the entire upstream suite or successful database connectivity.

Focused test selection:

```text
MongoshRunnerSecurityTest,MongoshRunnerTest,MongoshFileCreatorTest,MongoshChangeTest,MongoshFileChangeTest,MongoshStatementTest,MongoshExecutorTest,MongoshGeneratorTest,MongoConnectionTest,MongoConnectionVisibleUrlTest
```

## Reproduce the candidate tests on Linux

**Where:** EC2 Session Manager, after starting the same approved instance and verifying its fresh two-hour stop deadline. **Prerequisites:** the existing `/opt/mongodb-lab/build-001` tool downloads and a credential-free checkout/copy of the current lab files. Set `LAB_REPO` to that actual directory; never paste a token into a clone URL.

The complete automated build and container probes are now in `infra/delegate/build-native-candidate.sh`, tested from reviewed repository commit `4f5706401a58ae5bab9c6bb8bcb120ad08ee959f`. From that checkout run `sudo bash infra/delegate/build-native-candidate.sh`. It requires the existing build-001 downloads and active stop timer, creates `/opt/mongodb-lab/build-native-001`, and refuses to overwrite an existing build. Choose a fresh build path in a reviewed copy for a repeat. It does not register or promote the image. The expanded source-test portion below explains the same pinned patch/test workflow. The normal `prepare-on-ec2.sh` still builds the original lab.1 image with only patch 001.

```bash
# First change to the actual directory containing this lab repository.
set -Eeuo pipefail
LAB_REPO="$PWD"
test -f "$LAB_REPO/infra/delegate/patches/002-native-environment.patch"
LAB_TOOLS=/opt/mongodb-lab/build-001
LAB_CANDIDATE="$(mktemp -d /tmp/mongodb-native-candidate.XXXXXX)"
systemctl is-active --quiet mongodb-lab-autostop.timer
systemctl list-timers --all mongodb-lab-autostop.timer --no-pager
"$LAB_TOOLS/mongosh/bin/mongosh" --version

git clone https://github.com/harness-community/liquibase-mongodb-extension.git "$LAB_CANDIDATE/source"
git -C "$LAB_CANDIDATE/source" checkout --detach 1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021
for LAB_PATCH in 001-redact-visible-url.patch 002-native-environment.patch; do
  git -C "$LAB_CANDIDATE/source" apply --check "$LAB_REPO/infra/delegate/patches/$LAB_PATCH"
  git -C "$LAB_CANDIDATE/source" apply "$LAB_REPO/infra/delegate/patches/$LAB_PATCH"
done

"$LAB_TOOLS/apache-maven-3.9.16/bin/mvn" -B -ntp \
  -f "$LAB_CANDIDATE/source/pom.xml" \
  -Dmaven.repo.local="$LAB_TOOLS/maven-cache" \
  -Dtest=MongoshRunnerSecurityTest,MongoshRunnerTest,MongoshFileCreatorTest,MongoshChangeTest,MongoshFileChangeTest,MongoshStatementTest,MongoshExecutorTest,MongoshGeneratorTest,MongoConnectionTest,MongoConnectionVisibleUrlTest \
  -Dlab.mongosh="$LAB_TOOLS/mongosh/bin/mongosh" package
```

Expected: 62 tests, no failures/errors, only the existing DNS skip; specifically confirm the real mongosh security test ran and did not skip. A version check alone does not pass this step. Record the actual Linux JAR hash; build metadata may differ from the mini.

The automated build creates the separate image with the hash-checked libraries, mongosh 2.10.0, wrapper and pinned Dockerfile. It runs RuntimeProbe and NativeFailureProbe in containers with networking disabled and requires nonempty mongosh-owned logs without the synthetic canary. Require `NATIVE_CANDIDATE_BUILD_PASS`, `NATIVE_MONGOSH_LOGS_CLEAN` and zero exit. Logs are `maven-test.log`, `validation/runtime.log`, and `validation/native.log` under build-native-001; the parent log is `/opt/mongodb-lab/native-candidate-build.log`.

## Observed Linux artifacts

| Artifact | Value |
|---|---|
| Candidate image | `mongodb-lab-delegate:lab.native-candidate` |
| Image ID | `sha256:dbbe662baa881398d88262981262c74f325e5bb5bc5b8cf911e8ad3781bfb663` |
| Linux extension JAR SHA-256 | `07dc953dea798763ab530f62dd8250a48991225729de13f8ad9d90e5c512674a` |
| User / final result | `1001` / `NATIVE_BUILD_EXIT=0` |

Linux and mini hashes differ because source packaging includes build metadata; neither is claimed byte-identical to the vendor release. The same pinned source and reviewed patches were used.

## Remaining acceptance

Preserve lab.1 for rollback. Replace the registered container through a controlled procedure that retains hostname, limits, token file and read-only Git mount; reinstall copied helper classes and verify Harness reconnects. Real native authentication, fixtures, repeat/no-op behavior, scoped rollback, failure and concurrency are still required. Do not activate 003 on the original image.

Atlas user/secret, delegate registration and read-only GitHub access are already saved and approved. The first driver authentication attempt failed; see [the Atlas checkpoint](atlas-connectivity-checkpoint.md). Password correction and the phone-after-lock test are the remaining desk handoffs.
