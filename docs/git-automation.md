# GitHub PR to Harness automation — implementation checkpoint

**September 7, 2026: preparation only; no webhook or automatic deployment is enabled.**

The original handoff called for manual runs first and a protected-branch trigger after validation. The manual migration acceptance passed on September 6. This page tracks the remaining automation separately. GitHub pull requests are the lab equivalent of GitLab merge requests; the repository is not being moved to GitLab.

## 1. Complete the current account handoffs

1. In the prepared GitHub tab, complete **Confirm access** for `Korrojo`. Use the passkey, GitHub Mobile, or password option on GitHub itself. The tab is at `https://github.com/settings/apps/new`. No GitHub App has been created yet.
2. In the AWS tab, sign in to account `224772450208` as `lab-admin`, including MFA if requested. The earlier EC2 page is stale: AWS reported that the console session expired. The last verified shutdown remains the September 6 checkpoint, not a fresh observation.
3. Decide whether to upgrade the personal GitHub account to Pro. The private repository's branch-rule page reports that rules will not be enforced under the current plan. GitHub documents Pro support for protected private branches and a published price of $4/month, before applicable tax. A purchase has not been authorized or made. Keeping the current plan still allows the trigger demonstration, but cannot be described as enforced merge protection.
4. After account access is restored, review the actual GitHub App installation and narrowly scoped AWS role before granting the new access. The authentication handoffs above unlock those forms; they are not proof the integrations are installed.

Do not paste credentials into this task, YAML, Git, or screenshots. The existing Atlas secret and read-only Git deploy key do not need to be replaced for this work. The broad Harness OAuth request was cancelled because it requested access to public and private repositories across the account.

## 2. Review the intended access

| Integration | Intended scope | Purpose |
|---|---|---|
| GitHub App for Harness | Only `Korrojo/mongodb-liquibase-harness-lab`; contents, metadata and PR information read; commit statuses and repository webhooks write | Read code/event information, receive PR and push events, report check results |
| AWS role `mongodb-lab-github-wake` | Start `i-0635332c43aa733a5` only; read EC2 startup status in `us-east-1` | Wake the existing delegate when a Git event arrives |
| Existing Atlas secret | Deployment pipeline only | Apply reviewed migrations to `liquibase_lab` |

The proposed AWS policy and trust document are [github-wake-policy.json](../infra/aws/github-wake-policy.json) and [github-wake-trust.json](../infra/aws/github-wake-trust.json). They are local files, not installed IAM policies. They allow no instance creation, stop/termination, SSM session, database access, or IAM administration. EC2 describe APIs require `Resource: "*"`; the read operations are restricted to `us-east-1`. The trust is limited to the lab repository's PR subject and existing default branch, with the STS audience. The GitHub OIDC provider must first be inspected and reused or created deliberately.

The exact GitHub App permissions must be tested against Harness connector and webhook creation. Harness's general App guide includes content and PR write permissions for additional features; this lab should start with read permissions for those features and widen only if a demonstrated required operation needs it. A repository URL in a connector alone does not restrict the underlying credential.

## 3. Configure the connection after approval

1. Register the GitHub App using the supported Harness GitHub App flow. Set the homepage to `https://harness.io/` and disable the App's own webhook receiver; Harness creates repository webhooks separately. Harness's guide requires an App installable on any account. This does not make the lab repository public, but the App registration itself is public.
2. Install it on **Only select repositories**, selecting the one lab repository. Record App ID and installation ID, not a private key value.
3. Generate its private key, convert to the required PKCS#8 format, and save it through Harness's encrypted file-secret UI. Keep the local key outside Git with restrictive permissions. Do not output it to a terminal transcript.
4. Finish the prepared project connector `mongodb-lab-github` / `mongodblabgithub`: repository URL `https://github.com/Korrojo/mongodb-liquibase-harness-lab`, HTTP, GitHub App authentication and API access. Use Harness connectivity for GitHub API operations if available so webhook setup does not depend on a running delegate. Require the connection test to pass and read back the saved connector.
5. Separately verify how this account reports statuses from a **Custom** stage. Do not assume CI-stage automatic status reporting also applies to the existing Custom-stage pipelines. If a dedicated status publisher is required, it must use trusted installed code and the App credential, never code taken from a PR. Never publish a successful check before every required validation has passed.

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

**Local result on September 7:** Java 17 compilation and all 13 preflight scenarios passed. Both proposed IAM JSON documents parsed successfully and the repository whitespace check passed. No Harness execution has used this checker yet.

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

## 7. Acceptance sequence — all pending live execution

1. Start with the delegate stopped. Open a harmless PR and prove the Git event wakes the existing instance, starts the intended Harness validation and attaches the result to the correct commit.
2. Submit malformed YAML in a new migration. Require failed PR status and no Atlas changes. If protection is enabled, verify GitHub blocks the merge.
3. Fix that same PR with a new commit. Require a new validation of the new SHA; the old success or failure must not satisfy it.
4. Verify an edit to any existing applied migration fails the check.
5. Merge a reviewed new synthetic migration. Confirm one expected deployment uses the merge/push SHA, changes only the lab target, and records the expected additional history entry. Avoid modifying the three existing applied migrations.
6. Redeliver the event or repeat the accepted deployment. Require a no-op with unchanged data/history. Test overlap exclusion and stale-event handling.
7. Close an unmerged PR and push an unrelated branch. Require no deployment.
8. Exercise safe stop while work is active, restart from a later event, and verify no password or App key appears in execution logs.
9. Record the actual PR, delivery, execution links, commit SHAs, state assertions and instance state. Update the main runbook and minimal diagram only after these results exist.

Sources: [Harness GitHub App setup](https://developer.harness.io/docs/platform/connectors/code-repositories/git-hub-app-support/), [Harness Git event triggers](https://developer.harness.io/3k-docs/platform/triggers/triggering-pipelines/), [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches), [GitHub plans](https://docs.github.com/en/get-started/learning-about-github/githubs-plans), [published Pro pricing](https://docs.github.com/en/get-started/learning-about-github/faq-about-changes-to-githubs-plans), [AWS GitHub OIDC trust](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html).
