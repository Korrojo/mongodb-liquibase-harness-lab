# Repeat the GitHub → Harness → Atlas workflow

This is the short operating procedure after the infrastructure and pipelines in [Git automation](git-automation.md) are installed. The full manual setup remains in [the lab runbook](runbook.md).

## Submit a database change

1. Fetch `origin` and start a new branch from `origin/setup/lab-foundation`. Preserve all previously applied migration files, IDs and paths.
2. Add a new numbered YAML changeset under `changelog/changes/`. Append its include to `changelog/db.changelog-master.yaml`. Review both the intended change and its rollback definition; defining a rollback does not execute it.
3. Commit and push the new branch, then open a GitHub PR targeting `setup/lab-foundation`.
4. GitHub wakes the existing EC2 delegate. Harness waits three minutes and checks the exact proposed revision without connecting to Atlas or executing proposed JavaScript.
5. In the PR, require **mongodb-lab/pr-preflight: success** for the latest head. Follow its Harness link to inspect any failure. Correct the branch and push again; never bypass the required check.
6. Keep the PR up to date and use **Create a merge commit**. Squash/rebase merges are outside this lab's supported deployment path.
7. The protected-branch push automatically launches **mongodb-lab-merged-deploy**. Its five-minute startup wait is normal even when the host is already running.
8. Require `APPROVED_MERGE` with the new merge SHA, successful validate/update, `DEPLOYMENT_STATE_PASS`, and `MERGED_DEPLOYMENT_PASS`. The last marker confirms the second update left the verified state unchanged.
9. In Atlas → Cluster0 → Data Explorer → `liquibase_lab`, inspect the expected index and `DATABASECHANGELOG`. For the approved 004 lesson, expect four executed changesets, three unchanged synthetic documents and `lab_fixture_lookup` on `{labFixture: 1}` alongside the original unique SKU index.

The 004 lesson's change is already included in this checkout. Do not create another copy or reuse its ID when repeating the operating procedure. Use a new reviewed change or a documentation-only PR for a no-op deployment demonstration.

## Understand the two results

A green PR check means the proposed files passed the offline rules. It is not evidence of a database deployment. The separate post-merge Harness result proves what happened in Atlas. A failed deployment does not undo the Git merge; inspect the failed phase and database state before preparing a corrective PR. No automatic rollback or history reset is configured.

If another merge has advanced the branch while an older event waits, the older event is deliberately rejected before database access. Inspect the newer execution; do not force the old SHA through the guard.

## Leave the desk

The service-to-service flow runs in GitHub, Harness and AWS after setup. It does not depend on a browser tab remaining open. The two-hour EC2 timer drains running jobs before stopping; a later event can start it again. GitHub observes availability for ten minutes after each event, so manually stopping during that window can cause a restart.

For manual AWS assistance, open AWS Console → CloudShell or EC2 → Connect → Session Manager. Browser login/MFA may still require the account owner. No local AWS access keys are part of this lab.

## Preserve the staged exercises

The original manual exercise pipeline's phase-three verification expects the earlier three-changeset baseline. After 004, use the normal deployment verification above. Do not run rollback lessons against this retained four-changeset state merely to make the old phase check pass.
