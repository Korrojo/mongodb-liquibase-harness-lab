# Native runtime repair: local candidate checkpoint

Verified September 6, 2026 on the Mac mini. **This candidate has not been built into the EC2 image or tested against Atlas.** The stopped host still holds `mongodb-lab-delegate:lab.1` from the Linux checkpoint. Exercise 003 remains inactive.

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
| Linux mongosh 2.10.0, candidate container, live authentication, fixtures and rollback | **Not tested** |

The first test iteration caught a duplicate stream-cleanup call. The final implementation uses a single dedicated output reader and passed the cleanup checks. An offline packaging attempt lacked the Maven JAR plugin; an approved dependency download resolved that and the full focused package run passed. These results do not represent the entire upstream suite or successful database connectivity.

Focused test selection:

```text
MongoshRunnerSecurityTest,MongoshRunnerTest,MongoshFileCreatorTest,MongoshChangeTest,MongoshFileChangeTest,MongoshStatementTest,MongoshExecutorTest,MongoshGeneratorTest,MongoConnectionTest,MongoConnectionVisibleUrlTest
```

## Reproduce the candidate tests on Linux

**Where:** EC2 Session Manager, after starting the same approved instance and verifying its fresh two-hour stop deadline. **Prerequisites:** the existing `/opt/mongodb-lab/build-001` tool downloads and a credential-free checkout/copy of the current lab files. Set `LAB_REPO` to that actual directory; never paste a token into a clone URL.

These are the next prepared commands; their Linux execution is pending. Open a root Bash shell with `sudo -i`, then change to the actual lab-file directory. The normal `prepare-on-ec2.sh` still builds the previously verified image with only the visible-URL repair and does not apply patch 002 automatically.

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

Next, prepare a separate Docker build context with that candidate JAR, the hash-checked libraries, mongosh 2.10.0, the current wrapper, and the pinned Dockerfile. Build a distinct tag such as `mongodb-lab-delegate:lab.native-candidate`; preserve `lab.1`. Repeat the container extension and driver probes, exercise native failure inside that container, and inspect its temporary files and mongosh-owned logs. Successful Atlas authentication, fixtures, repeat execution, and rollback remain separate required checks. Promote a tested image explicitly and update its recorded fingerprint before enabling 003.

## Account handoff still pending

After the user's “Auth done” message, both prepared forms remained open and no saved lab-user/secret row was observed. GitHub authentication and private publication were already complete. The required user steps are:

1. Atlas: enter `liquibase_lab_user` and a new strong password in the prepared form. Keep the specific `readWrite` privilege on `liquibase_lab` and the Cluster0 restriction; select Add User.
2. Harness: put that same password in the prepared `atlas_password` Secret Value field and Save. Keep passwords out of chat and Git.
3. Confirm the separately prepared Harness delegate registration when ready. It connects the EC2 container `mongodb-lab` to Default Project and permits running the saved runtime check. The installer token stays in a root-only file; no inbound ports, Docker socket, or automatic upgrader are planned.

Browser credential-entry and new-access rules require those user actions. The existing AWS budget and SSM approval do not need to be repeated.
