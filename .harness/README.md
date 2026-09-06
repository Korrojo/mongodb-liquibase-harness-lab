# Harness configuration

`runtime-check.yaml` was copied from the saved `mongodb-lab-runtime-check` pipeline in Harness Default Project on September 6, 2026. The editor displayed Validated and Save was disabled. The complete YAML was exported through the editor clipboard. After approved delegate registration, Build 1 succeeded on `mongodb-lab`; see [the connection checkpoint](../docs/harness-connection-checkpoint.md). The pipeline definition was unchanged.

Expected baseline from the handoff: Custom stage, Bash Shell Script, On Delegate, selector `mongodb-lab`, timeout `2m`. Use the contents of `scripts/runtime-check.sh` for the tooling check after validating the actual schema.

The later migration pipeline must check out a reviewed exact commit, acquire a lock shared by all runs for the target database, perform preflight/validation/update/verification, preserve error exits, and clean per-run files. Keep all these operations in one orchestration script initially. GitHub connector creation alone does not provide a checkout inside a Shell Script step.

`atlas-connectivity.yaml` is saved and validated as `mongodb-lab-atlas-connectivity`. Its first execution failed with MongoSecurityException; password correction is pending. It injects the project secret as an environment variable and runs the read-only compiled Java helper. Install that helper using `infra/delegate/install-connectivity-probe.sh` after each container replacement. See [the Atlas checkpoint](../docs/atlas-connectivity-checkpoint.md) for exact steps and evidence.

`first-migration.yaml` prepares exact-commit checkout, a shared target lock, collection creation, and an immediate repeat/no-op check. It requires the installed first-migration helper and a passing Atlas authentication check. It has not been executed. See [the first migration checkpoint](../docs/first-migration-checkpoint.md).

Secret values do not belong in YAML. Record only project secret references once created through the supported account workflow. Keep triggers disabled during lab validation.
