# MongoDB Liquibase Harness lab — execution and replication runbook

**Verified execution edition, September 6, 2026.** The Atlas connection, initial and incremental migrations, native JavaScript, repeat execution, scoped rollbacks, invalid-YAML rejection and overlapping-run exclusion have passed on the personal lab. The final stop/start recovery test also passed; EC2 was left Stopped. See [validation-results.md](validation-results.md).

**Git automation update, September 7:** PR checks, protected merges, EC2 wake/shutdown coordination and post-merge deployment are covered in the [automation guide](git-automation.md). For everyday work, use the [short operating procedure](daily-workflow.md). The chapters below retain the earlier staged migration lesson. After changeset 004, use normal deployment verification rather than the phase-three manual check.

This is the working procedure derived from actual execution, including the fixes needed to make it pass. The supplied handoff is historical reference, not authorization or proof that a step succeeded. A complete installation in a second account has not been replayed. The infrastructure build uses pinned inputs, but a future build can produce different image/JAR hashes because of build metadata and package availability.

## 1. How we work remotely

The Mac mini manages the project and accessible browsers. Harness sends work to the EC2 delegate; Atlas stores the lab database. A submitted Harness execution can continue without the phone or MacBook remaining connected.

| Place | Work performed there |
|---|---|
| Phone, ChatGPT Remote | Continue this same task, review progress, give decisions and supported approvals |
| Mac mini, this task | Edit/review files, publish reviewed feature branches, operate the signed-in browsers |
| MacBook, Screen Sharing over Tailscale | Complete desktop login, MFA, secret entry or OS permission prompts when needed |
| AWS Console in the mini's browser | Create/reuse resources; inspect, start, connect to and stop the one EC2 instance |
| AWS Session Manager terminal | Linux host administration, Docker builds and helper installation |
| Harness pipeline | Checkout, lock, Liquibase commands, database assertions and cleanup |
| Atlas browser | Database-user settings and independent inspection of documents/history/indexes |

**AWS Console is the primary AWS interface for this lab.** Terminal commands below run on EC2 through Session Manager unless explicitly marked **Mac mini**. They are not AWS CLI commands, and no AWS CLI credential configuration is needed for this route.

The mini's AC sleep setting was 0. Display sleep, screen lock, logout, system sleep and an app/browser login expiring are different events. Keep the mini awake, online and the desktop app running. Tailscale provides connectivity; Screen Sharing provides the desktop view. Access after screen lock remains unverified: the Locked use setting was not confirmed, and a phone-after-lock test has not passed. Do not weaken login or security settings merely to assume unattended operation.

Before relying on the phone alone, complete this short acceptance test: from the phone continue this task and request a harmless file read plus a read of the already-open Harness page; lock the mini normally; repeat the same requests; then verify MacBook Screen Sharing over a phone hotspot reaches the same mini session. If a supported Locked use setting requires permission, complete it personally on the mini and retest. Browser login/MFA renewal may still require human help. No recurring background work has been scheduled.

## 2. Recorded personal-lab targets

| Item | Value |
|---|---|
| Public Git repository | [Korrojo/mongodb-liquibase-harness-lab](https://github.com/Korrojo/mongodb-liquibase-harness-lab/tree/setup/lab-foundation) |
| Working branch | `setup/lab-foundation` is protected; use feature branches and PRs |
| AWS | Learning-account `224772450208`, `us-east-1`, user `lab-admin` |
| EC2 | `i-0635332c43aa733a5`, name `mongodb-lab-delegate` |
| Harness | Account `7WPs0XUoT4CnMpX3j28V4g`, organization `default`, project `default_project` |
| Delegate / selector | `mongodb-lab` |
| Atlas | `PROJECT_01`, `Cluster0`, endpoint `cluster0.okiw7qi.mongodb.net` |
| Database / database user | `liquibase_lab` / `liquibase_lab_user`; authentication database `admin` |
| Atlas role | `readWrite` on `liquibase_lab`, restricted to Cluster0 |
| Harness encrypted Text secret | Project-scoped `atlas_password` |
| Host checkout | `/opt/mongodb-lab/repo` |
| Active container | `mongodb-lab`, native candidate image; UID 1001 |

[EC2 details](https://us-east-1.console.aws.amazon.com/ec2/home?region=us-east-1#InstanceDetails:instanceId=i-0635332c43aa733a5) · [Harness pipelines](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines) · [Atlas Data Explorer](https://cloud.mongodb.com/v2/6611f5088cbf733a72830464#/explorer/6611f5a08cbf733a72831a20)

These are personal-lab identifiers embedded in the scripts and YAML. A different account/repository/cluster/database requires a reviewed adaptation of all target guards, helpers, pipeline identifiers, origin URLs and permissions, followed by a new commit sequence. Do not paste these identifiers into an unrelated environment.

## 3. Choose the correct starting point

- **Resume the current automated lab:** follow [daily-workflow.md](daily-workflow.md). The section 9 `verify-final` check applies only to the historical three-changeset stage. Do not run `index-cycle` or `native-cycle` again against phase 3: their guards intentionally require the previous phase.
- **Repeat the migration lessons:** use the existing infrastructure and a confirmed empty, disposable `liquibase_lab`, then follow sections 6–10 in order. Clearing the existing database would erase the retained evidence and needs a deliberate reset decision; it has not been done by this task.
- **Rebuild the EC2 runtime:** follow [build-and-install.md](build-and-install.md), then sections 5–10. Preserve the old host/volume until the replacement has passed verification. The build instructions are for the recorded personal accounts; they do not promise a byte-identical new image.

The safe everyday verification path is nondestructive. No collection drop or history repair is needed to resume.

## 4. Start a working session

1. Open the EC2 details link. Check the account, region, name and exact instance ID. Use the page's **Refresh instances** button. During this lab, a stale single-page view continued to say Running until that button was used.
2. If Stopped, choose **Instance state → Start instance**. Reuse this instance. Wait for Running and status checks, then **Connect → Session Manager → Connect**. No SSH port/key pair is required.
3. In the new Session Manager terminal, run these read-only checks:

```bash
sudo systemctl is-active amazon-ssm-agent docker mongodb-lab-autostop.timer
sudo systemctl list-timers --all mongodb-lab-autostop.timer --no-pager
sudo docker inspect mongodb-lab --format 'STATE={{.State.Status}} HEALTH={{.State.Health.Status}} IMAGE={{.Image}}'
sudo docker inspect mongodb-lab --format 'MOUNTS={{range .Mounts}}{{.Destination}}:rw={{.RW}} {{end}}'
```

4. Require the three services active, the container running and healthy, the expected image from [versions.md](versions.md), and `/opt/mongodb-lab/git:rw=false`. Initial container health can be briefly unhealthy during startup; wait for healthy before a run.
5. Record the actual stop deadline. The boot-armed timer stops the instance after two hours; it does not gracefully drain a pipeline. Do not start a seven-minute migration step with less than ten minutes remaining. Finish and stop the session before the deadline rather than silently disabling the safeguard.
6. Read the current public IPv4 from the refreshed EC2 details page. It changes after stop/start. Atlas currently has a pre-existing `0.0.0.0/0` rule among six rules; those rules were left unchanged. This lab therefore does **not** demonstrate a narrow `/32` network boundary. On a separately reviewed restricted configuration, update the EC2 `/32` after each new address; the browser's Add Current IP normally selects the mini's address.
7. In Harness Project Settings → Delegates, require `mongodb-lab` Connected. A stopped EC2 delegate being disconnected is expected. Never start the preserved backup containers alongside the active delegate.

## 5. Secrets and read-only connectivity

Use the **Atlas database-user password**, not the Atlas website password or the Harness website password.

1. Atlas → Database Access → edit `liquibase_lab_user`. Verify `readWrite @ liquibase_lab`, Cluster0 scope and SCRAM. If changing its password, save that change in Atlas first.
2. Harness → Project Settings → Secrets → `atlas_password` → Edit. Paste that exact raw password into **Secret Value** and Save. Do not add quotation marks, URL encoding or a connection string. The prepared visible browser handoff was necessary when an earlier hidden tab was not visible to the user.
3. Never put the password in this task, Git, YAML text, a command argument or a screenshot. The YAML's supported environment variable value is `<+secrets.getValue("atlas_password")>`.
4. Run `mongodb-lab-atlas-connectivity`, with **Skip preflight check unchecked**. Require Harness Success and `ATLAS_CONNECTIVITY_PASS`. This uses the real delegate, TLS, an authenticated ping and a collection-name read; it writes no database objects.

After saving the matching Harness secret, Build 3 passed. The two earlier MongoSecurityException failures are retained as historical evidence, not current blockers. If authentication fails again, stop at this read-only check and reconcile the lab user's saved password and scope.

## 6. Save and run the supplied pipelines

The five credential-free definitions are in `.harness/`:

| YAML | Pipeline | Purpose |
|---|---|---|
| `runtime-check.yaml` | `mongodb-lab-runtime-check` | Actual delegate tool versions |
| `atlas-connectivity.yaml` | `mongodb-lab-atlas-connectivity` | Java-driver authenticated read |
| `first-migration.yaml` | `mongodb-lab-first-migration` | Phase 1 plus repeat/no-op |
| `native-connectivity.yaml` | `mongodb-lab-native-connectivity` | Real mongosh read and credential-handling assertions |
| `exercises.yaml` | `mongodb-lab-exercises` | Incremental cycles and acceptance exercises |

For a missing pipeline, open Harness → Pipelines → Create a Pipeline, use the name above, choose INLINE storage, open **YAML → Edit YAML → Enable editing**, replace the full document with its matching repository file, and Save. Wait for **Validated** and the Save button to become disabled. Existing matching pipelines should be reused. The first-migration saved YAML was read back and matched the repository definition.

These use Custom stages and Shell Script steps on the delegate, not the paid Database DevOps module. A visible menu or trial banner does not establish future entitlement; these particular pipelines executed successfully in this account on the recorded date.

For each run choose **Run**, fill the inputs listed below, uncheck **Skip preflight check**, and choose **Run Pipeline**. `LAB_COMMIT` must be the complete 40-character SHA, not a branch name. Do not type a secret into either input. Check the run's status and the log's **Bottom** button; logs are virtualized and the initial visible lines may omit the final result.

### Exact revision sequence

| Lesson | `LAB_COMMIT` | `LAB_EXERCISE` | Required starting state |
|---|---|---|---|
| First migration pipeline | `19f335da99e7079961a07f623db09acac5ec7a45` | No such input | Empty lab or verified phase 1 |
| Index cycle | `5ba7cd478b547a09957b59bfe37e58733f54e88f` | `index-cycle` | Phase 1 |
| Native fixture cycle | `232adee3f6477edd14dbeed6a828a7366d19862d` | `native-cycle` | Phase 2, native runtime verified |
| Invalid YAML | `e9a7bd6ba2d2f19f84d6e42a91d71ae6e6faf6e4` | `validation-failure` | Phase 3 |
| Overlap test, twice | `e9a7bd6ba2d2f19f84d6e42a91d71ae6e6faf6e4` | `concurrency` | Phase 3 |
| Everyday/restart verification | `e9a7bd6ba2d2f19f84d6e42a91d71ae6e6faf6e4` | `verify-final` | Phase 3 |

Do not use the latest master for the first exercise: it includes all three changesets. Applied paths, IDs, authors, checksums and script contents stay immutable. Preserve this private branch's history so the phase commits remain available.

## 7. Collection and index lessons

**Phase 1:** Run the first-migration pipeline at its exact revision. Its one step acquires the target lock, creates a private temporary directory, checks out the exact SHA, performs the state guard, runs `validate`, `status`, `update`, verifies state, runs `update` again and verifies the same fingerprint. It then checks status and removes its own temporary directory.

Require `FIRST_MIGRATION_AND_REPEAT_PASS`. The state must contain only `lab_items`, `DATABASECHANGELOG` and `DATABASECHANGELOGLOCK`; one executed 001 history row with checksum; zero fixture documents; the default `_id_` index; and no active database lock. The repeat adds no changeset or document.

**Phase 2:** Run exercises with `index-cycle` at its revision. It starts from phase 1, validates, applies 002, repeats the update without state change, runs `rollback-count --count=1`, verifies phase 1, and reapplies 002. Require `INCREMENTAL_REPEAT_PASS`, `SCOPED_ROLLBACK_PASS` and `EXERCISE_CYCLE_PASS exercise=index-cycle`.

Final phase 2 has two history rows, zero documents and the named unique `{sku:1}` index `lab_sku_unique` in addition to `_id_`. The rollback removes only that named index. Changeset 001 deliberately has no automated collection-dropping rollback.

### Permission setting discovered during the first run

The first attempt failed because tracking-table adjustment tried `collMod`, which this readWrite user could not run. The working scripts set:

```bash
export LIQUIBASE_MONGODB_SUPPORTS_VALIDATOR=false
```

This disables tracking-collection schema validator changes while retaining the default tracking-table adjustment and its unique history index. We did not grant a broader Atlas role. Liquibase history, checksums and database locking remain enabled; schema validation of those tracking documents is the tradeoff. This was a permission failure, not proof that Atlas Free cannot support validators. [MongoDB collMod privileges](https://www.mongodb.com/docs/manual/reference/command/collMod/).

## 8. Native JavaScript lesson

Before 003, the patched native runtime must be active and `mongodb-lab-native-connectivity` must pass. Require `LIVE_NATIVE_READ_ONLY_PASS` and `NATIVE_ATLAS_ACCEPTANCE_PASS`. This real Atlas check asserts that the connection URI/password are absent from process arguments and generated script text, temporary script permissions are 0600, the script is removed, and nonempty mongosh-owned logs contain no raw/encoded password or complete URI.

The original artifact needed credential-handling repairs. Both reviewed patches in `infra/delegate/patches/` are required for native execution. The repaired URI is passed through the child environment and removed before the user script executes. Privileged processes or another process with equivalent OS access can still inspect environments; this is a dedicated trusted runtime, not isolation from root or arbitrary untrusted code.

Run exercises with `native-cycle` at the exact revision. Changeset 003 explicitly selects:

```yaml
runWith: mongosh
```

Its `mongoFile` applies only three synthetic fixtures. Omitting that selector originally sent MongoshStatement to the wrong executor. Before fixing it, Atlas was checked and showed 003 had not been applied; no executed changeset was edited. [Harness native MongoDB examples](https://developer.harness.io/docs/database-devops/concepts/database-devops/concepts/mongodb-command/).

The exercise applies, repeats, rolls back the fixtures and reapplies. Require `INCREMENTAL_REPEAT_PASS`, `SCOPED_ROLLBACK_PASS`, `NATIVE_EXERCISE_FILES_PASS` and `EXERCISE_CYCLE_PASS exercise=native-cycle`.

Final phase 3 is exactly:

| `_id` | `sku` | `name` |
|---|---|---|
| `lab-001` | `LAB-001` | Synthetic notebook |
| `lab-002` | `LAB-002` | Synthetic pencil |
| `lab-003` | `LAB-003` | Synthetic folder |

All three carry `labFixture: mongodb-liquibase-harness`. The rollback deletes only these reserved IDs with that fixture marker, preserving the collection and its index. The guard refuses conflicting reserved IDs. History has one executed row for each of 001, 002 and 003, with checksums; the lock is released. Inspect Atlas Data Explorer → `liquibase_lab` → `lab_items` → **Refresh documents** to confirm the three records independently.

## 9. Failure, overlap and restart lessons

**Invalid YAML:** Run `validation-failure`. It creates a deliberately malformed private file and explicitly supplies its search directory. It requires a nonzero Liquibase exit and parser-specific text such as `while parsing a flow node`, checks the phase-3 fingerprint is unchanged, and prints `VALIDATION_FAILURE_BLOCKED_PASS`. The Harness exercise succeeds because the expected rejection was verified; no `update` is invoked. An earlier missing-file rejection was insufficient and is excluded from acceptance.

**Overlapping runs:** Open the exercises pipeline in two tabs. Prepare both with `concurrency` and the revision above. Start the first; wait for `CONCURRENCY_LOCK_HELD`, which holds the shared lock for 45 seconds; then immediately start the second. Expected: one run succeeds with `FINAL_NOOP_PASS`; the other fails with `LAB_BUSY` and exit 75 before checkout. This expected failed Harness execution demonstrates exclusion. Do not treat two serialized successes as proof of overlap.

The host `flock` covers only scripts using `/opt/mongodb-lab/locks/cluster0-liquibase_lab.lock` on this dedicated container. It is not a distributed lock across unrelated hosts. Liquibase's database lock remains an additional safeguard. Only reviewed trusted commits are allowed to use the saved secret.

**Restart:** After all executions finish, verify cleanup with the following command in the EC2 Session Manager terminal:

```bash
sudo docker exec -u 1001 mongodb-lab bash -c 'set -e; flock -n /opt/mongodb-lab/locks/cluster0-liquibase_lab.lock true; test -z "$(find /opt/mongodb-lab/work -mindepth 1 -maxdepth 1 -print -quit)"; printf "WORKSPACE_CLEAN_AND_TARGET_UNLOCKED_PASS\n"'
```

Then EC2 → Instance state → Stop instance, leave **Skip OS shutdown unchecked**, Stop, and use Refresh instances until **Stopped**. Start that same instance and repeat section 4. The image, deploy key, helper classes and database history must survive. Run `verify-final`; require `FINAL_NOOP_PASS`. The script validates, runs a no-op update and compares history, indexes and fixture fingerprints before/after. It does not re-run the destructive parts of the cycle.

Normal exit and handled termination clean the per-run workspace. Host failure or forced termination can bypass cleanup; inspect leftovers before any narrowly scoped removal. Never automatically clear checksums, delete history, release a database lock or drop data to make a failed check green.

## 10. Stop, retain evidence and resume later

1. Wait for every active Harness execution to finish. Check the cleanup marker above and the final database guard.
2. Record exact commit, pipeline build/execution ID, PASS markers, image/JAR hashes and any expected failure. Use [validation-results.md](validation-results.md) as the acceptance ledger.
3. Gracefully stop the same EC2 instance and refresh until Stopped. Closing a browser, disconnecting SSM or closing the phone does not stop EC2.
4. Retain the encrypted EBS volume, active image, stopped backups, private repository and Atlas evidence. Stopped EBS still costs money; termination/volume deletion or a database reset is a separate deliberate cleanup decision and was not performed.
5. For a later session, resume at section 4 and `verify-final`. A disconnected delegate while the instance is stopped is expected.

The approved budget was $20/month before credits. The September 6 picker showed $0.09576/hour compute; public IPv4 was $0.005/hour and 30 GiB gp3 approximately $2.40/month. About 40 running hours plus a full month of that disk is $6.43 before credits, taxes, transfer and extras. Prices and billing totals need a fresh check when repeating later. The two-hour timer is a cost safeguard, not a budget enforcement service. [AWS IPv4 pricing](https://aws.amazon.com/vpc/pricing/) · [EBS pricing](https://aws.amazon.com/ebs/pricing/).

## 11. Troubleshooting by observed symptom

| Symptom | Check and resolution |
|---|---|
| Harness `MongoSecurityException` | Match the Atlas database-user password to Harness `atlas_password`; check admin auth database and Cluster0 user scope; rerun read-only connectivity first |
| `collMod` denied, error 8000 | Use the tested validator setting in section 7; retain unique history-index adjustment; do not grant admin as a shortcut |
| `Unknown type: ...MongoshStatement` | Require `runWith: mongosh` and patched runtime; inspect history first; do not edit an already executed changeset |
| Changelog “does not exist” | Use a correct resource search path and relative changelog name; a missing-file error is not YAML validation evidence |
| `LAB_BUSY`, exit 75 | Another run owns the target lock; let it finish, inspect outcome and retry only if appropriate |
| Phase guard fails | Inspect actual history, indexes and fixtures. Use the required starting phase/revision; don't reset metadata |
| Delegate unavailable after stop | Start the same EC2, verify timer/Docker/health and Connected; check its renewed public IP if network rules are restricted |
| Java source launch fails in container | It has a JRE. Compile helpers using the EC2 host JDK and the supplied installation scripts |
| Probe class missing after container replacement | Reinstall all helper classes; they are copied into the container, not baked into the image |
| Permission denied when redirecting host build output | Put redirection inside `sudo bash -c '...'` or use a writable `/tmp` log; outer-shell redirection is not elevated |
| AWS details appear stale | Use the actual Refresh instances control; navigating to the same URL may preserve cached state |
| User cannot see password form | Open a visible side-browser tab and identify Secret Value and Save; do not assume a hidden tab is visible |
| Native build probe fails on master count | Build from the recorded 001-only revision; RuntimeProbe expects one master changeset at build time |

## 12. Scope and limits of the evidence

The account's actual community-extension workflow passed the listed executions. This is not a production availability, disaster-recovery, load or penetration test. Native log/argument/script assertions cover the tested normal and negative paths, not every possible failure. Atlas's existing broad network rule was unchanged; managed backups were inactive. Only the restricted lab namespace was exercised; no cross-database before/after inventory was possible with the lab user. The Mac locked-screen remote workflow and a completely fresh-account rebuild remain unverified.
