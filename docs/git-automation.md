# GitHub → Harness → MongoDB lab automation

## Verified scope — September 7, 2026

PR events launch Harness checks, GitHub enforces the required status, and GitHub starts the existing EC2 delegate through its scoped AWS role. Normal merges trigger a separate Harness deployment that verifies the reviewed revision, applies Liquibase changes and proves the repeat is a no-op. See the acceptance table below for actual executions and the [daily workflow](daily-workflow.md) for later use.

| Component | Current state |
|---|---|
| GitHub repository | Public; no Pro subscription |
| Deployment branch | `setup/lab-foundation`, protected including administrators |
| Harness GitHub connection | `mongodblabgithub`, OAuth; connection test passed |
| PR pipeline / trigger | `mongodblabprpreflight` / `mongodblabprevents`; enabled and tested |
| AWS role | `mongodb-lab-github-wake`; installed and successfully assumed |
| Normal deployment pipeline | `mongodblabmergeddeploy`; enabled and tested with a protected-merge webhook |
| Normal deployment trigger | `mongodblabmergedevents`; enabled; exact repository and protected-branch filters |
| Shutdown | Lifecycle locking installed; active-lock drain test passed |
| Atlas | Four executed changesets, three original documents, new fixture lookup index; repeat no-op passed |

GitHub pull requests are the equivalent of the GitLab merge requests discussed earlier. This lab stays on GitHub.

## 1. Start from the completed manual lab

Complete the [main runbook](runbook.md) and [runtime installation](build-and-install.md) first. The expected baseline is three synthetic documents, the unique SKU index, three executed changesets and no held Liquibase lock in `liquibase_lab`. Do not modify the bytes, IDs or paths of those applied changesets.

Use the existing EC2 instance `i-0635332c43aa733a5` in account `224772450208`, region `us-east-1`, and its `mongodb-lab` Docker delegate. Keep the retained images, EBS volume and Atlas database. There is no need to create another instance, database user or delegate.

AWS administration uses the signed-in console, CloudShell and Session Manager. No local AWS access keys are required. The Mac needs Git and authenticated GitHub CLI for the repository steps. Browser sign-in/MFA may require the user; credentials must stay in the service's authentication UI.

## 2. Configure GitHub and Harness connectivity

1. Use `Korrojo/mongodb-liquibase-harness-lab`. This personal account's private repository could not enforce branch protection on its free plan. The user chose public visibility instead of Pro.
2. Before publishing any comparable repository, scan its entire reachable history and manually inspect matches. This lab's Gitleaks 8.30.1 scan covered 23 commits and reported no leaks. Lab IDs, the Atlas hostname and documentation are public; that scan is evidence, not a guarantee about future commits.
3. In Harness Default Project → Project Settings → Connectors, create or reuse **mongodb-lab-github**, identifier **mongodblabgithub**.
4. Select GitHub, repository URL, HTTP authentication, **OAuth**, OAuth API access and connectivity through the **Harness Platform**. Complete GitHub authorization, require a successful connection test, then save. The user completed OAuth for this lab. The earlier GitHub App proposal is superseded.
5. The connector's repository URL does not narrow the underlying OAuth grant. Keep the managed token inside Harness. Pipeline YAML contains only its secret reference; use the actual managed reference when replicating in another account.

## 3. Install the trusted PR checker

The candidate PR must never supply the checker that decides whether it passes. The installed Java classes are trusted code; proposed JavaScript is inspected but never executed during PR validation.

On the EC2 host, through Session Manager, fetch a reviewed exact repository commit into a root-owned build directory. From that checkout run:

```bash
bash infra/delegate/install-pr-probes.sh
```

The installer compiles Java 17 classes on the host, copies them into the running delegate, makes them root-owned and non-writable by UID 1001, and runs the metadata self-test. It prints hashes to compare with [pr-probes.sha256](../infra/delegate/pr-probes.sha256).

The delegate contains a JRE, not a JDK. Java source-file launching failed there with `jdk.compiler` missing; do not replace the compiled-class approach with source launching. Reinstall the helpers after replacing the container; a normal EC2 stop/start retains them.

The checker enforces unchanged existing migrations/scripts, append-only ordered master includes, valid YAML without duplicate keys, supported change types, unique IDs/authors, valid JSON fields, and paths confined to the checkout without symlinks. It also parses the complete master with the recorded Liquibase runtime. Thirteen local positive/negative scenarios passed. This constrained lab check does not prove arbitrary MongoDB commands or native JavaScript are semantically safe.

## 4. Create the PR pipeline and publish its first status

1. Create inline pipeline **mongodb-lab-pr-preflight**, ID **mongodblabprpreflight**, using [pr-preflight.yaml](../.harness/pr-preflight.yaml).
2. Verify the installed class hashes match its checksum block. Its Shell Script runs on delegate selector `mongodb-lab` as UID 1001.
3. Keep `LAB_COMMIT` and `LAB_PR_NUMBER` as runtime inputs. Supply the managed GitHub OAuth secret reference, **not** `atlas_password`.
4. Open a same-repository PR targeting `setup/lab-foundation`. Run the pipeline manually once using the PR number and exact 40-character head SHA. Do not substitute a branch name.
5. Require both Harness success and GitHub status **mongodb-lab/pr-preflight: success** on that exact SHA, with the execution link. A Harness success alone is insufficient.

The runner reads the current PR through GitHub, fetches exact base/head commits, validates them, then rereads the PR to reject a result whose head or base changed during checking. It uses a private per-run directory and cleans it on normal exit/handled termination. Errors identify the failed phase without printing API responses or credentials.

## 5. Protect the deployment branch

Configure protection on **setup/lab-foundation**:

- Require status `mongodb-lab/pr-preflight`.
- Require the PR branch to be up to date.
- Apply the rules to administrators.
- Block force pushes and branch deletion; require conversation resolution.
- Do not require an additional approving reviewer in this one-person lab: the author cannot approve their own PR.

Use normal merge commits. The deployment guard deliberately rejects squash/rebase forms and checks that the merge tree is identical to the reviewed PR tree. No administrator bypass was used for bootstrap PR1.

## 6. Configure PR events

Create the GitHub webhook trigger from [pr-trigger.yaml](../.harness/pr-trigger.yaml), initially disabled. Inspect the saved YAML before enabling it:

| Field | Value |
|---|---|
| Events | PR Open, Reopen, Synchronize |
| Target branch | `setup/lab-foundation` |
| Head repository | `Korrojo/mongodb-liquibase-harness-lab` |
| `LAB_COMMIT` | `<+trigger.payload.pull_request.head.sha>` |
| `LAB_PR_NUMBER` | `<+trigger.payload.number>` |

The saved trigger is `mongodblabprevents`; repository webhook `675815654` returned HTTP 200 for actual PR events. No optional webhook secret was configured. The trusted runner independently checks repository, branch, PR state and exact SHA through GitHub before accepting a result.

The first webhook run failed because the delegate was stopped. The saved pipeline now uses a three-minute Harness Wait step followed by two one-minute retries of the offline check. This also delays a final negative result by two retry intervals. Exhausted retries fail; they never manufacture a passing status.

## 7. Set the exact GitHub OIDC identity

The default OIDC subject differed between push and PR events. To retain branch-specific AWS trust, configure GitHub to include the branch explicitly:

```bash
gh api --method PUT \
  repos/Korrojo/mongodb-liquibase-harness-lab/actions/oidc/customization/sub \
  --input infra/aws/github-oidc-subject.json

gh api repos/Korrojo/mongodb-liquibase-harness-lab/actions/oidc/customization/sub
```

The JSON specifies `use_default: false` and `include_claim_keys: ["repo", "ref"]`. Live tokens use this repository's immutable identity prefix:

```text
repo:Korrojo@133557745/mongodb-liquibase-harness-lab@1359386091
```

The allowed subject ends with `:ref:refs/heads/setup/lab-foundation`. Audience is `sts.amazonaws.com`. For another repository, inspect its actual identity; do not copy this lab's IDs. Inspect selected non-secret claims when diagnosing OIDC, never print the token. The correction did not widen AWS trust to all PRs or use a wildcard.

## 8. Install the approved start-only AWS role

Review [github-wake-trust.json](../infra/aws/github-wake-trust.json), [github-wake-policy.json](../infra/aws/github-wake-policy.json) and [the installer](../infra/aws/install-github-wake-role.sh). In authenticated AWS CloudShell, verify the account, stage the three files from the same reviewed commit and verify their hashes before execution.

```bash
aws sts get-caller-identity --query Account --output text
bash install-github-wake-role.sh
```

The installer creates/reuses the GitHub OIDC provider and creates role **mongodb-lab-github-wake**. It intentionally fails if that role already exists rather than overwriting another role. For this lab it is already installed: use `get-role` and `get-role-policy` for inspection, not a second installation.

The role may start only the existing instance and describe EC2 startup state in `us-east-1`. It cannot create or terminate instances, open SSM sessions, access Atlas or administer IAM. GitHub receives temporary credentials. The user explicitly approved this grant.

## 9. Activate the wake workflow through a protected merge

[Wake workflow](../.github/workflows/wake-delegate.yml) uses a pinned official AWS action and runs trusted default-branch code. Its PR path rejects fork heads and never checks out candidate code. `pull_request_target` requires the workflow on the default branch; bootstrap PR1 was merged only after its required Harness check passed.

A push on the deployment branch also wakes the host. EC2 health is not proof that the Harness delegate is immediately eligible; the Harness wait/retry remains necessary. The merged workflow observes availability for ten minutes so a stop at the old boot's deadline can be followed by another start. This is independent of the shorter Harness startup wait.

## 10. Coordinate shutdown with running jobs

The PR, first-migration and exercise pipelines now hold a shared lifecycle lock and reject the `draining` marker before starting work. The normal deployment runner uses the same mechanism and retains the separate database lock.

After those live pipeline guards are installed, run [install-safe-autostop.sh](../infra/aws/install-safe-autostop.sh) as root on EC2. It keeps the existing two-hour timer, sets the draining marker at shutdown, waits up to fifteen minutes for existing lifecycle locks, then stops the host. It does not forcibly power off if the lock wait fails. The startup service clears the marker when the container returns. Inspect the failure if draining exceeds the bound; do not release database locks blindly.

Acceptance used a systemd-managed simulated job holding the actual lifecycle lock for sixty seconds. The shutdown service remained pending with the marker present (`DRAIN_WAIT_PASS`), and EC2 was independently observed Stopped after the job finished. This tests coordinated normal shutdown, not arbitrary crashes or SIGKILL.

## 11. Prepare normal merged deployment

On EC2, from the reviewed checkout:

```bash
bash infra/delegate/install-deployment-probes.sh
```

Compare hashes with [deployment-probes.sha256](../infra/delegate/deployment-probes.sha256). Create **mongodb-lab-merged-deploy**, ID **mongodblabmergeddeploy**, from [merged-deploy.yaml](../.harness/merged-deploy.yaml). It has a five-minute startup wait and invokes installed checksum-pinned code, with Atlas and GitHub managed secret references.

The guard requires the event SHA to be the current deployment-branch commit, associated with a merged same-repository PR, with a successful required preflight and an unchanged reviewed merge tree. It rechecks before database work while holding the target lock. The runner then validates, updates, verifies history/retained fixtures, repeats the update, and compares state to prove the repeat is a no-op. It does not automate rollback or clear history/locks. GitHub credentials are removed from the environment before Liquibase runs.

Create [merged-trigger.yaml](../.harness/merged-trigger.yaml) **disabled**. It filters repository and exact protected branch, and maps `<+trigger.payload.after>` to `LAB_COMMIT`. Never attach this trigger to the exercise pipeline, which deliberately runs rollback lessons.

**Approved acceptance scope:** the user authorized baseline validation/update/repeat and one new non-unique `lab_fixture_lookup` index on `{labFixture: 1}`. Baseline acceptance passed before trigger activation. The three original migration files and three documents are preserved. No rollback, deletion or reset is included.

## 12. Acceptance evidence

| Test | Evidence / status |
|---|---|
| Manual trusted PR check | [72SEZC7iRpKh6tTpFEWFUQ](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabprpreflight/executions/72SEZC7iRpKh6tTpFEWFUQ/pipeline): passed |
| Actual synchronize event | [VMVkxkhlRASpCX_G_yYK_Q](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabprpreflight/executions/VMVkxkhlRASpCX_G_yYK_Q/pipeline): passed; exact status enforced before PR1 merge |
| Malformed new YAML | [3sJ9ynPwRCiPNiM0lj82FQ](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabprpreflight/executions/3sJ9ynPwRCiPNiM0lj82FQ/pipeline): failed at changelog inspection; required GitHub status failed |
| Corrected PR head | [JH75hDezQVONdXUm_ner_w](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabprpreflight/executions/JH75hDezQVONdXUm_ner_w/pipeline): passed on `fa1ece9c70899c69b623e0fd8c6eb46a4d21d8d0` |
| AWS role and restart | [34143979367](https://github.com/Korrojo/mongodb-liquibase-harness-lab/actions/runs/34143979367): passed after explicit OIDC branch customization and a rerun |
| Merge boundary tests | Real reviewed merge accepted; ten invalid metadata/status variants rejected locally |
| Shutdown coordination | `DRAIN_WAIT_PASS`; stopped state verified after the simulated job released its lock |
| Fresh cold event after OIDC fix | [Wake 34145422986](https://github.com/Korrojo/mongodb-liquibase-harness-lab/actions/runs/34145422986) and [Harness bQmqDX51TMey0ckSKK1kMQ](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabprpreflight/executions/bQmqDX51TMey0ckSKK1kMQ/pipeline) passed from a reopened PR; no manual rerun |
| Approved manual baseline | [fkzUquhkSR-4zPOoTnao3w](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabmergeddeploy/executions/fkzUquhkSR-4zPOoTnao3w/pipeline): passed on `80764471f93ac582176a48058f9dde9e6e53339f`; history 3, documents 3, identical before/after fingerprint |
| Stale revision live rejection | [2G-UHiS1ReWrF4JDalny0g](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabmergeddeploy/executions/2G-UHiS1ReWrF4JDalny0g/pipeline): expected failure, `DEPLOYMENT_REJECTED` before Atlas work for obsolete `8076447` |
| New index through PR4 merge | [ZJIXg-GeSCeqt9khOybrJQ](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabmergeddeploy/executions/ZJIXg-GeSCeqt9khOybrJQ/pipeline): passed on `fc78e57a335426629a80ffdd74601c0c035db7c0`; one changeset applied, history 4, documents 3, repeat ran 0 |
| First automatic merged deployment | [cCfVuIfSQnGVAoew6fOTiw](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabmergeddeploy/executions/cCfVuIfSQnGVAoew6fOTiw/pipeline): passed on PR3 merge `221799a1786b2ae6225be592a89aa0b02cf21983`; repeat no-op |

For replication, first prove a baseline no-op, then enable the inspected trigger. Use a separate PR for a new migration, require its exact-head check, merge normally and inspect the automatic deployment result. Keep test PR2 closed and unmerged; it only demonstrated rejection and recovery. Do not infer database success from a configured trigger or green PR check.

Sources: [Harness Git triggers](https://developer.harness.io/3k-docs/platform/triggers/triggering-pipelines/), [Wait step](https://developer.harness.io/docs/continuous-delivery/x-platform-cd-features/cd-steps/utilities/wait-step/), [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches), [GitHub OIDC](https://docs.github.com/en/actions/reference/security/oidc), [AWS OIDC roles](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html).
