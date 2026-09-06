# Harness configuration

Export the actual saved `mongodb-lab-runtime-check` pipeline from Default Project after browser access is restored. No complete YAML was exported in this task, so no reconstructed pipeline is represented here as an account-validated export.

Expected baseline from the handoff: Custom stage, Bash Shell Script, On Delegate, selector `mongodb-lab`, timeout `2m`. Use the contents of `scripts/runtime-check.sh` for the tooling check after validating the actual schema.

The later migration pipeline must check out a reviewed exact commit, acquire a lock shared by all runs for the target database, perform preflight/validation/update/verification, preserve error exits, and clean per-run files. Keep all these operations in one orchestration script initially. GitHub connector creation alone does not provide a checkout inside a Shell Script step.

Secret values do not belong in YAML. Record only project secret references once created through the supported account workflow. Keep triggers disabled during lab validation.
