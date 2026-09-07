# GitHub PR to Harness automation — implementation checkpoint

**September 7, 2026: public repository, enforced branch protection and a successful manual PR check; webhook and automatic deployment acceptance are still pending.**

The original handoff called for manual runs first and a protected-branch trigger after validation. The manual migration acceptance passed on September 6. This page tracks the remaining automation separately. GitHub pull requests are the lab equivalent of GitLab merge requests; the repository is not being moved to GitLab.

## 1. Verified account configuration

On September 7, the user selected the free GitHub plan and authorized making this lab repository public if needed for branch protection. GitHub returned HTTP 403 for protection on the private repository. Before publication, Gitleaks 8.30.1 scanned all 23 reachable commits (318,670 bytes) with no leaks reported; manual inspection classified pattern matches as test canaries or environment references. The repository was then made public. Lab resource identifiers and documentation are consequently public too.

GitHub protection on `setup/lab-foundation` now requires `mongodb-lab/pr-preflight`, requires the branch to be up to date, applies to administrators, blocks force pushes and deletion, and requires conversation resolution. No second-person review is required in this one-person lab. No GitHub Pro purchase is needed.

The user completed Harness GitHub **OAuth** authorization. The connector `mongodb-lab-github` / `mongodblabgithub` is saved and its connection test passed. It uses repository URL `https://github.com/Korrojo/mongodb-liquibase-harness-lab`, HTTP/OAuth authentication, OAuth API access, and connectivity through the Harness Platform. The earlier GitHub App proposal was abandoned; do not create another integration or request an App key. A repository URL does not narrow the underlying OAuth grant.

## 2. Remaining AWS access

The user approved the scoped grant and role `mongodb-lab-github-wake` is now installed. AWS readback verified its exact trust and inline permissions. Its [permission policy](../infra/aws/github-wake-policy.json) permits starting only `i-0635332c43aa733a5` and reading EC2 startup state in `us-east-1`. It grants no instance creation, termination, SSM, Atlas or IAM administration. Read-only discovery in authenticated AWS CloudShell returned no existing OIDC providers.

The [trust document](../infra/aws/github-wake-trust.json) is installed and its AWS readback matches the reviewed file. It matches only the protected default branch and the immutable owner/repository identity. [GitHub diagnostic run 34130998996](https://github.com/Korrojo/mongodb-liquibase-harness-lab/actions/runs/34130998996) succeeded on September 7 at 10:05 AM Eastern and confirmed the token uses `repo:Korrojo@133557745/mongodb-liquibase-harness-lab@1359386091:ref:refs/heads/automation/git-events`. The proposed trust uses that same verified identity with `setup/lab-foundation` as the permitted branch. The diagnostic printed only selected identity claims, never the token, obtained no AWS credentials, and executed no PR code. Acceptance must still prove the eventual default-branch workflow can assume the installed role. The temporary diagnostic workflow must be removed before merging the completed automation.

The intended wake workflow uses trusted default-branch code and must never check out or execute proposed PR code. Availability and safe shutdown remain pending; starting an instance is not proof that Harness can immediately assign work to it.

## 3. Replicate the saved PR pipeline

1. In Harness project settings, create a GitHub connector using the repository URL, HTTP/OAuth authentication and OAuth API access. Complete authorization on GitHub and require the connection test to pass. This lab already has that connector; reuse it.
2. Start the existing EC2 instance and wait for health checks and the Harness delegate to become available. Connect through AWS Console → EC2 → Connect → Session Manager. Retain the budget timer while doing installation work.
3. Fetch a reviewed exact commit containing the checker, runner and installer onto the host. Run `infra/delegate/install-pr-probes.sh` as root. It compiles Java 17 classes on the host, installs them into the delegate and checks ownership and its self-test. The delegate image contains a JRE, so Java source-file launching fails there because `jdk.compiler` is absent.
4. Record the installed class hashes and compare them with [pr-probes.sha256](../infra/delegate/pr-probes.sha256). If compilation produces different hashes, inspect the source, compiler and outputs; do not simply bypass the checksum check.
5. Create the inline Custom-stage pipeline from [pr-preflight.yaml](../.harness/pr-preflight.yaml). Keep `LAB_COMMIT` and `LAB_PR_NUMBER` as runtime inputs. Replace the OAuth secret reference with the actual managed reference in the replicating account. Never paste its value into YAML or Git.
6. Run against an open, same-repository PR targeting `setup/lab-foundation`, using its exact head SHA and PR number. This pipeline receives no Atlas password. It executes the installed trusted runner, fetches exact base/head commits, validates files without executing candidate scripts, and publishes GitHub pending then success/failure on the tested head.
7. Inspect the Harness log and independently read the GitHub commit status. A green Harness run without the exact GitHub status is insufficient for branch protection.
8. Configure the required GitHub status and branch protection described above. Trigger creation and automatic-event acceptance below remain unfinished in this checkpoint.

**Verified manual run:** [Harness execution 72SEZC7iRpKh6tTpFEWFUQ](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabprpreflight/executions/72SEZC7iRpKh6tTpFEWFUQ/pipeline), September 7 at 09:54:44 AM Eastern, succeeded in 14 seconds for PR #1, head `831f14f58f052b1ed6b972eafc51f8ad259ae33b`, base `9827c55cdffeb318c0ab5d603648bda22aab57e4`. GitHub independently reported `mongodb-lab/pr-preflight: success` with that Harness execution link. No Atlas access was used.

The preceding two runs exposed and resolved a JRE/source-launch incompatibility and Harness's legacy `/ng/#/account/...` execution-link format. The runner now accepts both observed account-specific URL forms, and reports controlled phase names without printing credentials or API response bodies.

## 4. Install and verify the trusted PR preflight

The local implementation is [LabChangelogCheck.java](../scripts/LabChangelogCheck.java). It checks:

- Existing changelog and native script files remain unchanged compared with the trusted base checkout.
- Master includes remain in order and can only be appended.
- YAML is well formed, has no duplicate keys, and uses the supported lab change types.
- Referenced files stay inside the candidate checkout and do not use symlinks.
- Changeset identities are unique, required index fields are present, and MongoDB command/index JSON parses.
- The recorded Liquibase runtime can parse the complete master changelog.

This is a constrained lab preflight. It does not execute JavaScript, contact Atlas, prove a migration is semantically safe, or replace live Liquibase validation and review. It deliberately rejects features outside the lab's supported subset. Extending that subset requires updating and testing the trusted validator first.

Run these commands on the **Mac mini, from the task workspace above `lab/`**, using the already prepared runtime libraries and patched extension classes:

```bash
javac --release 17 \
  -cp 'outputs/runtime-libs/*:outputs/extension-source/target/classes' \
  -d outputs/git-automation-checks lab/scripts/LabChangelogCheck.java
python3 lab/scripts/test-pr-preflight.py \
  'outputs/git-automation-checks:outputs/runtime-libs/*:outputs/extension-source/target/classes'
```

The tests use temporary changelog copies and no database credentials. They do not alter applied migrations or the retained Atlas evidence.

**Local result on September 7:** Java 17 compilation and all 13 preflight scenarios passed. Both proposed IAM JSON documents parsed successfully and the repository whitespace check passed. The manual Harness execution recorded above also passed with the installed checker.

Before use in Harness, install the reviewed compiled checker with the trusted runtime on the delegate. The PR pipeline must execute that installed version, not compile or run a checker from the candidate PR. Fetch an exact candidate commit and an exact base commit into separate per-run directories. Compare the candidate to the current target-branch base, and ensure a base-branch change invalidates or reruns the PR result. Use no `atlas_password` reference in this pipeline. A passing result must be attached to the exact PR head SHA, with its Harness execution link.

## 5. Prepare deployment and availability

1. Create a separate normal deployment pipeline. Do not attach Git triggers to `mongodb-lab-exercises`: its index/native lessons intentionally roll back and reapply changes, and its phase assertions are fixed to three changesets.
2. Preserve the existing database lock, exact-commit checkout, private per-run working directory, redacted logs, timeout, cleanup and failure exits. Run live validation before update. Perform post-update verification and a repeat/no-op check. Keep rollback deliberate, outside the automatic push path.
3. Map the protected/default-branch push event's exact commit SHA to deployment input. Verify repository and target branch. Reject a stale or unrelated commit, and verify it corresponds to an accepted merged PR with a passing preflight for the reviewed contents. Do not merely fetch a mutable branch name and assume it is the event commit.
4. Add a small GitHub Actions wake job using temporary AWS credentials through OIDC and the reviewed wake role. Its only AWS task is starting the existing EC2 instance and observing startup. It must not execute proposed PR code or receive Atlas credentials. Harness still orchestrates the migration.
5. Resolve startup timing before enabling triggers: the wake job and webhook arrive independently, so prove a Harness job can wait for delegate startup rather than immediately fail. Use explicit bounded waiting or an authenticated dispatch-after-ready design if required by the observed account behavior.
6. Replace or adapt the existing two-hour automatic-stop behavior only after testing safe coordination. The current timer can interrupt a running migration. Stop must wait for active work to finish, prevent a new job starting during shutdown, and retain a cost limit. An instance already running near its deadline also needs a defined path. This remains unimplemented; do not claim unattended readiness yet.

## 6. Configure event rules and merge protection

Retain `setup/lab-foundation` as the deployment branch unless deliberately renamed. Develop automation on `automation/git-events`; do not push directly to `main` or quietly change the default branch.

| Event | Intended result | Database access |
|---|---|---|
| PR opened, reopened, or updated against `setup/lab-foundation` | Check the exact PR head; publish pending then success/failure | None |
| Merge resulting in a push to `setup/lab-foundation` | Deploy the accepted event commit | Lab database only |
| PR closed without merge, unrelated branch/repository, stale event | No deployment | None |

Create the triggers disabled first and inspect their saved YAML and runtime-input mapping. Filter both the repository and target branch. Configure the GitHub repository webhook through the tested connector and verify its delivery history. Once the first real PR check has been published, require that exact status context in branch protection, apply it to administrators, and require the branch to be up to date. For a one-person lab, do not require an additional human review that the PR author cannot supply. If the GitHub plan cannot enforce these rules, record that gap explicitly.

## 7. Automatic-event acceptance sequence — pending live execution

1. Start with the delegate stopped. Open a harmless PR and prove the Git event wakes the existing instance, starts the intended Harness validation and attaches the result to the correct commit.
2. Submit malformed YAML in a new migration. Require failed PR status and no Atlas changes. If protection is enabled, verify GitHub blocks the merge.
3. Fix that same PR with a new commit. Require a new validation of the new SHA; the old success or failure must not satisfy it.
4. Verify an edit to any existing applied migration fails the check.
5. Merge a reviewed new synthetic migration. Confirm one expected deployment uses the merge/push SHA, changes only the lab target, and records the expected additional history entry. Avoid modifying the three existing applied migrations.
6. Redeliver the event or repeat the accepted deployment. Require a no-op with unchanged data/history. Test overlap exclusion and stale-event handling.
7. Close an unmerged PR and push an unrelated branch. Require no deployment.
8. Exercise safe stop while work is active, restart from a later event, and verify no password or OAuth token appears in execution logs.
9. Record the actual PR, delivery, execution links, commit SHAs, state assertions and instance state. Update the main runbook and minimal diagram only after these results exist.

Sources: [Harness Git event triggers](https://developer.harness.io/3k-docs/platform/triggers/triggering-pipelines/), [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches), [GitHub OIDC reference](https://docs.github.com/en/actions/reference/security/oidc), [AWS GitHub OIDC trust](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html).

### Live trigger installation checkpoint

`mongodblabprevents` was saved disabled, inspected, then enabled. Its [exported configuration](../.harness/pr-trigger.yaml) maps the exact PR head SHA and PR number and excludes fork heads. GitHub webhook `675815654` is active with HTTP 200 last response. Automatic execution acceptance is now being tested; webhook delivery alone is not a passing pipeline.
