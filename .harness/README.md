# Harness configuration

`runtime-check.yaml` was copied from the saved `mongodb-lab-runtime-check` pipeline in Harness Default Project on September 6, 2026. The editor displayed Validated and Save was disabled. The complete YAML was exported through the editor clipboard. After approved delegate registration, Build 1 succeeded on `mongodb-lab`; see [the connection checkpoint](../docs/harness-connection-checkpoint.md). The pipeline definition was unchanged.

Expected baseline from the handoff: Custom stage, Bash Shell Script, On Delegate, selector `mongodb-lab`, timeout `2m`. Use the contents of `scripts/runtime-check.sh` for the tooling check after validating the actual schema.

The later migration pipeline must check out a reviewed exact commit, acquire a lock shared by all runs for the target database, perform preflight/validation/update/verification, preserve error exits, and clean per-run files. Keep all these operations in one orchestration script initially. GitHub connector creation alone does not provide a checkout inside a Shell Script step.

`atlas-connectivity.yaml` is saved and validated as `mongodb-lab-atlas-connectivity`. After the recorded password correction, Build 3 passed. It injects the project secret as an environment variable and runs the read-only compiled Java helper. Install that helper using `infra/delegate/install-connectivity-probe.sh` after each container replacement. See [the acceptance ledger](../docs/validation-results.md) for current evidence; the earlier connectivity checkpoint is historical.

`first-migration.yaml` implements exact-commit checkout, a shared target lock, collection creation, and an immediate repeat/no-op check. It passed during the manual lab, followed by incremental index and native migration acceptance through `exercises.yaml`. See [the current runbook](../docs/runbook.md).

Secret values do not belong in YAML. Use managed secret references. Automatic PR checks and protected-merge deployments are configured and tested; see [Git automation](../docs/git-automation.md) for the execution ledger. Never attach the normal post-merge trigger to a pipeline that intentionally exercises rollback.

`pr-preflight.yaml` is the saved Custom-stage `mongodb-lab-pr-preflight` pipeline. Its manual acceptance succeeded on September 7 and published the required `mongodb-lab/pr-preflight` GitHub status. It uses installed trusted compiled classes, exact PR head/base checkouts, and the managed OAuth secret; no Atlas secret or candidate script execution. Reinstall and verify both helper classes after replacing the delegate container. Automatic PR webhook acceptance, required status publication, cold startup and coordinated shutdown have passed; see [Git automation](../docs/git-automation.md).

`merged-deploy.yaml` is the normal deployment pipeline. It verifies a protected normal merge, then uses installed trusted code to validate, update and prove the repeat is a no-op. The user approved baseline and incremental-index acceptance. `merged-trigger.yaml` is initially disabled for replication; enable only after baseline acceptance. Deployment results are recorded in the Git automation guide.
