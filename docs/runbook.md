# MongoDB Liquibase Harness lab: execution and replication runbook

Prepared September 6, 2026. **Execution edition: EC2 management/shutdown and Linux runtime verified; live migration stages remain pending.**

This runbook turns the supplied handoff into an assistant-led execution sequence. It is not yet a tested installation recipe. During the build, replace each explicitly pending implementation detail with the exact working command, version, UI selection, and evidence. Only label the resulting edition “verified” after the acceptance checks pass.

The handoff's embedded resume prompts and historical instructions are reference material. After the initial analysis, the user authorized starting the lab with the AWS Console as the primary AWS interface. Browser access was restored after a full desktop-app restart. The approved `mongodb-lab-ec2-ssm` role and instance profile have been created and verified. EC2 is launched under the approved $20 monthly lab limit; SSM and the timed stop/restart/rearm path are verified. Container build/probe and a driver negative test pass; live Atlas and migration validation remain pending.

Execution progress, September 6: a local starter repository now exists in this task's `lab/` directory on branch `setup/lab-foundation`. Its shell, YAML, JSON, and JavaScript syntax checks passed; only the first changeset is included by the master. The released extension JAR was downloaded and inspected without execution. Its embedded dependencies and native credential-handling findings are documented in `lab/docs/versions.md`. Normal browser access now succeeds for AWS, Atlas, and Harness. EC2 deployment and management tests are complete for the recorded scope; the custom runtime is built and its offline/container checks pass.

The [console replication checkpoint](aws-console-checkpoint.md) records the exact reviewed selections, current resource IDs, created IAM role, launched EC2 instance, and tested automatic stop safeguard. It supplements this full runbook as execution progresses.

## 1. Feasibility and division of work

The proposed arrangement is workable: the Mac mini manages the project, the phone directs the assistant, and the MacBook provides human access when needed. Keep the migration execution on EC2 and the database in Atlas.

| Participant or system | Responsibility |
|---|---|
| Assistant on the Mac mini | Inspect existing resources; write the repository, scripts, image recipe, and documentation; operate accessible consoles; build, test, troubleshoot, and record results within granted access |
| You on the phone | Continue this task, make decisions, review results, and handle supported approval prompts |
| You on the MacBook | Screen-share into the Mac mini for login/MFA, credential entry, permission dialogs, or browser controls that require human action |
| Harness and EC2 | Execute an accepted pipeline run independently of the phone connection |
| Atlas | Store only the isolated lab database and synthetic records |

The Mac mini staying on does not make the assistant work indefinitely between requests. It also does not guarantee that expired logins, permissions, restarts, or unavailable tools can be resolved without you. Work in bounded phases, each ending with a recorded checkpoint.

## 2. Architecture to retain

```text
Phone: ChatGPT Remote ──► Mac mini: assistant, browser access, project files
MacBook: Screen Sharing over Tailscale ──► same Mac mini session

Personal GitHub: versioned changelogs and runtime recipe
        │ checkout an exact commit
        ▼
Harness: manual pipeline, secrets, execution history
        │ task delivered through the delegate's outbound connection
        ▼
EC2: Docker → persistent custom Harness delegate container
                  Git + Java + Liquibase + one MongoDB extension + mongosh
        │ TLS connection from EC2's allowlisted public IP
        ▼
Atlas Free: liquibase_lab

AWS Session Manager ──► EC2 host administration
```

Tailscale is the private network for your MacBook assistance. It is separate from ChatGPT Remote and from AWS Session Manager. Tailscale connectivity alone does not supply a screen-sharing application or authorize desktop control. Use the Mac's Screen Sharing facility over the private connection, with access restricted to your intended account. [Apple Screen Sharing](https://support.apple.com/guide/mac-help/share-the-screen-of-another-mac-mh14066/mac), [Tailscale quickstart](https://tailscale.com/docs/how-to/quickstart).

## 3. What this review established

| Item | Evidence and status on September 6 |
|---|---|
| Source handoff | Read from the iCloud Downloads Markdown file named in the request |
| Local project | Writable task workspace exists; `sources/` remains read-only reference material |
| Local capacity | Approximately 21 GiB available at inspection; large image builds should happen on EC2 |
| Mac power settings | AC sleep setting was 0; display sleep was 10 minutes |
| Browser tools | Chrome extension and in-app browser surfaces were available |
| Harness sign-in | Existing in-app tab was signed into the account named in the handoff |
| Saved pipeline | `mongodb-lab-runtime-check` opened in Default Project and displayed Validated; no run was started |
| Tailscale | Application reported running; MacBook-to-mini connectivity and screen control were not tested |
| Phone Remote | Supported by current documentation; this phone's pairing and this task's remote controls were not tested |
| AWS / Atlas | Live console inventory completed after restart; $100 AWS credits, $0 current-month spend; Atlas FREE/8.0.32, lab database absent, restricted lab user pending |
| EC2 and IAM | Approved role created; instance `i-0635332c43aa733a5` launched; Session Manager and timed stop/restart/rearm passed |
| Toolchain | Git, AWS CLI, GitHub CLI, and Docker commands exist locally; authentication, Docker daemon health, and runtime versions were not tested |

## 4. Improvements required before calling this reproducible

1. **Prove Remote access before spending.** Demonstrate local file access and browser reading from a phone prompt; test publication through the real permitted route once the lab repository exists.
2. **Capture actual account state.** A saved pipeline and a visible module do not establish successful execution or every required free entitlement.
3. **Pin the entire runtime.** Include the delegate image, migration Java, Liquibase, extension, dependency graph, mongosh, OS, and architecture. The reviewed extension documents `1.0.0-4.33.0` as based on Liquibase `4.33.0`; that is a candidate pair, not a tested compatibility claim. Its README also contains older dependency examples, so resolve dependencies from the selected release rather than copying that list. [Extension repository](https://github.com/harness-community/liquibase-mongodb-extension).
4. **Control custom-image upgrades.** Harness says delegate auto-upgrade can replace the custom image and remove added tools. Verify the applicable Docker delegate update mechanism, keep this custom runtime under explicit update control, and document how to rebuild and retest against supported base versions. Do not copy Kubernetes updater commands into this Docker installation. [Harness custom images](https://developer.harness.io/docs/platform/delegates/install-delegates/build-custom-delegate-images-with-third-party-tools/).
5. **Test credential handling through native execution.** A successful Java-driver connection does not prove `mongoFile` receives credentials safely. Inspect the chosen native executor and test both authentication paths without exposing secrets.
6. **Define working-directory and locking behavior.** Separate Shell Script steps can have different shells and delegate placement. For the initial one-delegate lab, use one orchestration script for checkout through verification and cleanup, with a shared target lock around the whole operation. Keep the Liquibase database lock too. [Harness delegate placement](https://developer.harness.io/docs/platform/delegates/manage-delegates/run-all-pipeline-steps-in-one-pod/).
7. **Make shutdown and IP renewal explicit.** A budget notification is not a shutdown mechanism; changing the Atlas allowlist after stop/start is part of the normal start procedure.

## 5. Ordered execution steps

### Step 0 — Establish the phone and browser workflow

**Where:** Mac mini desktop, phone, and MacBook. **Lead:** assistant checks; user completes pairing and human prompts.

1. On the mini, open ChatGPT desktop Settings → Connections → Control this Mac or PC. Reuse an existing working pairing; otherwise use Set up/Add and scan its QR code on the phone.
2. Use the same ChatGPT account and workspace on both devices. Keep the host awake, online, with the app running. Review Computer Use and browser access on the host. Remote uses the host's tools, credentials, and approval rules. [OpenAI Remote connections](https://learn.chatgpt.com/docs/remote-connections).
3. From the phone, open Remote, select the Mac mini, and continue this task. Ask the assistant to create and read back a harmless readiness note in this task's output directory, and read the already-open Harness pipeline.
4. On the MacBook, test screen sharing into the mini over Tailscale. Confirm you can interact with the same desktop account and browser profile used by this task. Test this away from the home LAN, such as through a phone hotspot.
5. Sign into AWS, GitHub, and Atlas in an assistant-accessible browser **on the mini**. The existing Harness in-app session already works. A browser signed in only on the MacBook does not provide the mini with that session.
6. Establish a handover convention: assistant identifies the page and exact action; user takes control; user reports completion; assistant rereads the page before continuing.

**Pass:** phone prompt completes a local and browser read; MacBook fallback reaches the same mini session. Record failures before depending on unattended access. Test any necessary approval interaction when a real action requests it; do not create a sensitive action merely to test approvals.

### Step 1 — Reconcile the four accounts

**Where:** accessible browsers on the mini. **Lead:** assistant, read-only first.

1. AWS: confirm the intended learning account and `us-east-1`. Inspect EC2 instances by name and tags, IAM role `mongodb-lab-ec2-ssm`, security groups, and relevant EBS volumes. Do not recreate a resource just because the handoff says it was absent.
2. Inspect the recorded default VPC and candidate subnet. Verify an attached internet gateway, subnet route to it, public-IP assignment, DNS, and network ACLs permitting required outbound traffic and replies.
3. Harness: confirm Default Project, the saved runtime pipeline, current delegate inventory, Docker installation path, relevant CD entitlement, built-in secret manager, and any trial expiration affecting these features. Export the actual pipeline definition when preparing the repository.
4. Atlas: identify the existing project and Free cluster, its state and region, current network rules, database users, and whether `liquibase_lab` already contains anything. Avoid broadening or deleting existing access.
5. GitHub: confirm the personal owner and whether a lab repository already exists. Choose a private repository for the new lab if none exists.

**Pass:** a dated resource inventory distinguishes “found,” “absent,” and “not accessible,” with exact non-secret identifiers and navigation links. Entitlement uncertainty must be resolved before launch, or explicitly accepted as a bounded runtime experiment.

### Step 2 — Prepare the working repository and documentation

**Where:** authorized writable local directory; then private GitHub repository. **Lead:** assistant.

1. Keep the source handoff and synced `sources/` files unchanged. Put authored files in a normal working repository; this review's `outputs/` directory is the initial deliverable location, not proof of GitHub publication or phone file synchronization.
2. Prepare a focused initial branch. Review the files before publication and verify that publication actually succeeds from this Remote workflow. Do not equate a local commit with a remote commit.
3. Establish the following deliverables; these paths describe files to implement, not files already created:

```text
README.md
.gitignore
.harness/runtime-check.yaml
.harness/mongodb-migrate.yaml
changelog/db.changelog-master.yaml
changelog/changes/001-create-collection.yaml
changelog/changes/002-create-index.yaml
changelog/changes/003-seed-data.yaml
changelog/scripts/003-seed-data.js
config/liquibase.properties.example
scripts/preflight.sh
scripts/run-lab.sh
scripts/verify.sh
scripts/rollback.sh
infra/delegate/Dockerfile
infra/delegate/versions.lock
infra/host-bootstrap.sh
docs/runbook.md
docs/resource-inventory.md
docs/versions.md
docs/validation-results.md
docs/cleanup.md
```

4. Ignore credentials, populated environment files, tokens, downloaded binaries, raw logs, and temporary workspaces. Keep only sanitized evidence and credential-free examples in Git.
5. For every final procedure, include: purpose, where it runs, prerequisites, exact action, placeholders, expected result, verification, failure handling, and restart/cleanup implications.

**Pass:** the starter repository is reviewable; its remote visibility and commit SHA are verified; no secrets are present.

### Step 3 — Finalize costs and the launch configuration

**Where:** AWS console. **Lead:** assistant prepares a concrete review.

Retain the handoff's proposed settings unless current account evidence requires a change:

| Setting | Proposed value or required check |
|---|---|
| Instance | One `mongodb-lab-delegate`, `m7i-flex.large`, Linux x86_64, 2 vCPU / 8 GiB |
| AMI | Current supported Amazon Linux 2023 x86_64 image; record actual AMI and owner instead of blindly reusing the old ID |
| Network | Verified public subnet in existing default VPC; automatically assigned public IPv4 |
| Security group | New lab-only group, zero inbound rules; outbound access for SSM, Harness, GitHub/software sources, DNS, and Atlas |
| Storage | 30 GiB encrypted gp3, baseline 3,000 IOPS / 125 MiB/s; record encryption key and delete-on-termination setting |
| Administration | Session Manager, no SSH key pair or inbound SSH |
| Instance role | `mongodb-lab-ec2-ssm`, EC2 trust, only `AmazonSSMManagedInstanceCore` |
| Metadata | IMDSv2 required, hop limit 1; migration container has no planned need for AWS role credentials |
| OS shutdown behavior | Stop, verified explicitly |
| Tags | Name, project, owner, and agreed cleanup date |

The live EC2 picker confirmed compute at $0.09576/hour on September 6. Console Home showed $100 credits and $0 month-to-date spend; billing can update later. Public IPv4 is currently $0.005/hour. Using the handoff's $2.40 for a full month of 30 GiB gp3, a conservative worksheet is:

| Running hours | Compute + IPv4 + full month of disk |
|---:|---:|
| 40 | $6.43 |
| 100 | $12.48 |
| 168 (continuous week) | $19.33 |

These are estimates before credits, taxes, transfer, and extras; compute was verified in the account picker, and storage/IP rates were checked against AWS public pricing. Storage continues while the instance is stopped and is prorated when released earlier. A continuous week leaves little room under a $20 ceiling. Reconfirm the earlier $10–20 budget as part of the actual launch review. [AWS public IPv4 pricing](https://aws.amazon.com/vpc/pricing/), [EBS billing and baseline performance](https://aws.amazon.com/ebs/pricing/).

**Pass:** actual price, available instance type, credit applicability, and final settings are recorded. The user has a concrete launch summary before any billable resource is submitted.

### Step 4 — Create the approved role and execution host

**Where:** AWS console. **Lead:** assistant; user handles required action-time confirmation.

1. If the role is absent, prepare IAM → Roles → Create role → AWS service → EC2. Select only `AmazonSSMManagedInstanceCore`, then set the agreed role name and inspect its trust and permissions.
2. At the final action, obtain any required confirmation for creating management access. The browser tool's current policy requires action-time confirmation for materially expanding security-sensitive access; the historical handoff is not that confirmation.
3. Create and attach the reviewed instance profile. Recheck the launch summary from Step 3, then launch only under the current execution authorization and budget.
4. Record instance ID, subnet, security group, root volume, role/profile, public IP, AMI, and launch time.
5. Open EC2 → instance → Connect → Session Manager. Verify the host identity and session access. The role supplies host SSM permissions; the signed-in administrator separately needs permission to start sessions. [AWS Session Manager](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager.html).
6. Establish a local shutdown safeguard independent of the assistant connection before leaving the host unattended. Recommended implementation: a boot-armed systemd timer with an agreed maximum session duration, a visible deadline, and a procedure to extend it before long work. Prevent new runs near the deadline and drain active work before planned shutdown. Test the timer; document that an OS shutdown timer is not an absolute protection against host failure.
7. Verify instance-initiated shutdown means **stop**, and test it before real migration work. Record that EBS persists and compute has reached Stopped in the AWS console. [AWS shutdown behavior](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/Using_ChangingInstanceInitiatedShutdownBehavior.html).

**Pass:** Session Manager works, inbound rules remain empty, storage is encrypted, and the tested shutdown mechanism stops the host. The timer installation script in `infra/aws/user-data.sh` ran successfully. A 45-second runtime override triggered a real EC2 stop; restart restored SSM and the two-hour timer. See the console checkpoint for the exact test and evidence.

### Step 5 — Build the migration runtime on EC2

**Where:** EC2 through Session Manager. **Lead:** assistant.

1. Install Docker Engine using the supported Amazon Linux package path verified for the selected AMI. Enable its service, verify it starts, and configure bounded logs. Record exact package versions and commands in `host-bootstrap.sh`.
2. Select the supported Harness Docker delegate image offered by the account. Resolve and record a tag and digest. Build on EC2 for Linux x86_64; do not accidentally publish an Apple Silicon image from the mini.
3. Build a custom image containing Bash, Git, CA certificates, required utilities, the migration Java runtime, Liquibase, exactly one MongoDB extension, its resolved dependencies, and mongosh. Preserve the Java requirements and startup behavior of the delegate itself; scope any separate Java setting to the migration command.
4. Inspect the selected release's dependency metadata, license, and native executor. Record sources and checksums. Do not mix the upstream MongoDB extension JAR with the Harness fork. Candidate Liquibase `4.33.0` / fork `1.0.0-4.33.0` remains provisional until tested.
5. Keep the registration token out of the image, Dockerfile, repository, chat, and logs. Provision it using a reviewed protected bootstrap mechanism. New credentials or their transfer to another service may need a user handover or specific confirmation through the browser.
6. Register the delegate with selector `mongodb-lab`, enable a restart policy, and verify explicit control of custom-image upgrades. Avoid privileged containers and Docker-socket mounting unless a demonstrated requirement changes the design.
7. Execute the saved runtime-check through Harness. Its expected result is the presence and versions of `git`, `java`, `liquibase`, and `mongosh` **inside the delegate**, then a successful step. That step does not test Atlas or the extension's change types.
8. Add a separate extension-load check and record the actual runtime location, user, versions, and image digest. Restart the container and repeat the check.

**Pass:** the Harness-triggered checks succeed on the intended container, with a reproducible image recipe and safe token handling. Installed tools on the host alone do not pass this step.

### Step 6 — Configure Atlas access and Harness secrets

**Where:** Atlas and Harness consoles, then the delegate runtime. **Lead:** assistant, with user credential actions as required.

1. Reuse the existing Free cluster. Confirm the target name is exactly `liquibase_lab` and that its contents are safe for this exercise.
2. Prepare a dedicated Atlas database user with `readWrite` on that database. Test whether the actual changes and Liquibase tracking require additional rights; widen only for a demonstrated need. The user completes credential creation/change UI when required by browser policy.
3. Add the current EC2 public IPv4 as a single `/32` entry. “Add current IP” in your local browser would normally select the mini's internet address, so enter the verified EC2 address explicitly. Do not use its private address or Tailscale address. [Atlas IP access lists](https://www.mongodb.com/docs/atlas/security/ip-access-list/).
4. Create project-scoped Harness secrets for the chosen Atlas authentication method and separate read-only repository authentication. Keep credential-free endpoint/database settings as ordinary configuration where supported. Record secret identifiers only.
5. Inject values through supported step environment/secret inputs. Disable command tracing and avoid embedding passwords or expanded URIs into script source, command arguments, or summaries. If the selected executor requires another mechanism, review and test it before proceeding; do not assume an environment variable reaches its child process.
6. From the real delegate runtime, test SRV/TXT DNS, TLS, the Atlas endpoint/port requirements, authenticated ping, and a safe read. Exercise both the Liquibase connection and the mongosh path required for native changes.
7. Verify the expected tracking collections and indexes can be created in the lab database during the controlled first update.

**Pass:** authenticated runtime connectivity works and logs contain no credentials. Atlas Free does not provide managed backups or private endpoints; this remains a disposable-data lab. [Atlas Free limitations](https://www.mongodb.com/docs/atlas/reference/free-shared-limitations/).

### Step 7 — Implement the migration contract and pipeline

**Where:** local repository and Harness; execution on EC2. **Lead:** assistant.

1. Define explicit, ordered changesets with stable IDs, authors, and paths. Keep applied changes immutable.
2. Use a small collection such as `lab_items`, a named index, and deterministic synthetic IDs. Include an actual Harness-fork `mongoFile` example. Give every tested reversal a precise scope; use seed-record deletion or a named-index removal for the first rollback exercise.
3. Split the rollout into commits so the first migration and later incremental migration can be proven independently. Do not include all later changes before the initial-repeat test.
4. Export and adapt the account-valid Custom stage/Shell Script configuration. Keep the runtime-check pipeline as a diagnostic. Create the migration pipeline with manual execution only.
5. Accept a validated revision; resolve it to an exact commit SHA. Use a safe Git checkout without credentials in the remote URL or printed command. The existence of a GitHub connector does not itself prove that a Shell Script step checks out the code.
6. In `run-lab.sh`, acquire a target-wide lock shared across its unique workspaces, create an isolated temporary directory, check out the recorded SHA, load runtime configuration, and enforce a fixed database guard. Allow only defined actions.
7. Run preflight, supported Liquibase `validate`, `status`, and `update`, then database assertions and a sanitized summary. Preserve failure exit codes. Always clean temporary credentials and workspaces. Retain non-secret evidence outside the removed workspace.
8. Do not let untrusted pull-request code execute with deployment secrets. For the lab, run only reviewed commits from the trusted repository. Keep migration credentials scoped to the lab database.
9. Verify the concurrency limit actually covers two attempts against this database. A selector tag alone does not serialize runs. A host lock supplements, rather than replaces, Liquibase's database lock.

**Pass:** a manual Harness run at a recorded commit applies the intended change, reports failure correctly when needed, and verifies the target state. Exact changelog syntax and CLI flags enter the verified runbook only after release-specific testing.

### Step 8 — Complete the acceptance evidence

**Where:** Harness execution history, Atlas lab database, repository. **Lead:** assistant.

| Test | Procedure and required evidence |
|---|---|
| First update | Run commit A. Verify intended collection/index/data plus Liquibase tracking; show only the lab database changed |
| Same-commit repeat | Rerun A. Show no new changesets and unchanged deterministic counts/indexes |
| Incremental update | Add a new changeset in commit B. Show only the additional change applies |
| Native executor | Apply the scoped `mongoFile` change through the fork; verify its expected result |
| Missing tool | Use a controlled preflight fixture; show a clear failure before database mutation |
| Validation failure | Use a disposable test branch with invalid changelog structure; show validation blocks update |
| Authentication failure | Use an isolated deliberately invalid test input, not rotation of the real secret; show failure without disclosure or database mutation |
| Migration failure | Use a deliberately failing, non-destructive test operation against synthetic data; inspect database and tracking state, then document forward recovery |
| Rollback | Reverse one explicitly reversible change, verify database and tracking, then reapply as supported |
| Concurrency | Start two controlled attempts; demonstrate queueing/rejection or serialized mutation and no duplicate effects |
| Restart recovery | Stop/start EC2, update Atlas access, verify delegate recovery, rerun B as a no-op |
| Rebuild | Rebuild the image from its recorded recipe/artifacts and repeat runtime and migration checks |

Record each result as PASS, FAIL, or NOT RUN, with date, commit SHA, image/version reference, Harness run link, and sanitized observations. A pipeline success badge alone is insufficient evidence of correct data.

MongoDB changes can partially succeed before an error. Before any retry, inspect data and tracking. Do not clear checksums, delete tracking collections, or release locks as routine repairs. Release a stale lock only after establishing that no migration is still running. The final lab should teach forward correction as well as the limits of rollback.

### Step 9 — Daily start, stop, and remote recovery

**Start:** open this task → review saved status → start the existing EC2 if stopped → wait for status checks and SSM → obtain current public IP → update the lab's Atlas `/32` if changed → remove only the obsolete lab entry once appropriate → wait for the rule to apply → verify delegate and runtime → check shutdown deadline → run a connectivity check → proceed at a known commit.

**Stop:** stop launching runs → wait for active migration completion and verify final state → save sanitized evidence and repository work → stop EC2 gracefully → confirm **Stopped** in AWS → record next step. Keep the mini available for Remote.

**Lost phone connection:** reconnect to the same task and inspect the existing Harness execution before starting another. A dispatched pipeline may have continued. Check for a running process or lock before retrying.

**Browser or permission issue:** assistant reports the exact page/action; user screen-shares into the mini; assistant pauses browser interaction during the handover and refreshes state afterward. If a tool is blocked, preserve work and report the blocker; do not repeatedly retry unchanged operations.

**Mac restart:** restore the intended desktop session, network, app, and browser access. Verify remotely before relying on it again; Tailscale reachability alone is insufficient.

### Step 10 — Produce the verified replication edition

**Where:** repository documentation. **Lead:** assistant, then user replay.

1. Replace provisional commands and version choices with tested ones; retain clearly marked alternatives only where they are useful.
2. Record the initial state required by each exercise and expected counts/index names/tracking results. Make examples fully credential-free with explicit placeholders.
3. Include the exported pipeline, image recipe and artifact digests, bootstrap script, safe credential-entry procedure, rollback limitations, troubleshooting, and cleanup inventory.
4. Walk through the documented path against a fresh lab database state and a rebuilt runtime. Record where a fresh EC2 or fresh-account replay was not performed rather than implying it was.
5. Publish reviewed changes and verify the remote commit. Use a release marker for the completed lab if desired. Preserve the resource map privately and avoid embedding account-specific identifiers into general examples.
6. Have you perform a guided replay from the runbook. Capture any missing explanation or hidden prerequisite and repair it before closing the lab.

**Pass:** a second operator can follow the documented steps, identify where each command runs, and reproduce the tested outcomes without reconstructing chat history.

### Step 11 — Cleanup when you finish

**Where:** the four service consoles. **Lead:** assistant prepares exact resource list; user supplies any required destructive-action confirmation.

1. Save required repository files, run links, and sanitized evidence. Disable triggers and prevent new runs.
2. Remove the lab delegate and revoke its registration token using the applicable Harness workflow. Revoke lab-only repository credentials and remove unneeded lab secrets.
3. Terminate only the verified lab EC2 when requested. Verify the root volume's fate; inspect remaining lab volumes, snapshots, and any allocated IPs. Stopping alone is not full teardown.
4. Remove only unused lab-created security groups and IAM role/profile. Do not delete the shared default VPC or shared networking.
5. Remove the disposable Atlas lab database, dedicated user, and lab IP entry as requested. Preserve a shared cluster and unrelated data.
6. Recheck the tagged AWS inventory and later billing records, allowing for billing delay. Record any retained paid resources explicitly.

## 6. Troubleshooting order

| Symptom | First checks |
|---|---|
| Phone cannot reach task | Correct host/account/workspace; mini awake and online; desktop app running; pairing |
| Browser inaccessible | Browser is on the mini; correct profile; accessible extension/in-app surface; login not expired |
| SSM cannot connect | Instance state, instance profile, agent, outbound network, and operator permissions; do not open SSH as the first workaround |
| Harness waits for delegate | Host/Docker running, registration, selector, connectivity, and task availability |
| Tools missing only in Harness | Actual container, PATH, execution user, image digest, and auto-upgrade behavior |
| Atlas timeout after restart | EC2 public IP versus Atlas entry, rule propagation, DNS, TLS and endpoint ports |
| Liquibase works, mongoFile fails | mongosh version/path, native executor loading, auth handoff, selected release compatibility |
| Migration failed after writing | Exact commit, partial data effects, tracking state, active processes/locks; design forward correction |
| Work appears lost in next step | Per-step working directory and delegate placement; use the single orchestration script |
| Git publication blocked remotely | Preserve local work, identify exact permission/authentication failure, complete required user action once |

## 7. Immediate next checkpoint

Complete the prepared Atlas/Harness credential handoff and approve the prepared delegate registration. The custom Linux image is built and verified; the private repository is created and initial publication is in progress. Real database connection, migration, repeat, rollback, and failure tests remain pending. Keep the native exercise inactive until its separate credential-handling issue is repaired and tested.

The [Linux runtime checkpoint](linux-runtime-checkpoint.md) records the exact build inputs, final image, tests, corrected CLI entry point, and remaining credential/registration gates.
