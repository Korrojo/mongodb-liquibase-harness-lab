# MongoDB Liquibase Harness lab

Status: EC2 deployed on September 6, 2026; Session Manager, timed stop, restart, and two-hour rearm verified. Atlas connection and migrations remain unrun. The local branch is `setup/lab-foundation`. The private GitHub repository is created; initial branch publication is in progress.

Architecture: private GitHub repository → Harness Custom stage/Shell Script → persistent Docker delegate on EC2 → Atlas `liquibase_lab`. The Mac mini is the management workstation.

## Current files

- `infra/aws/launch-plan.json`: proposed console configuration, with unknowns explicitly unset; not an AWS CLI request.
- `infra/aws/ec2-trust-policy.json`: EC2 trust policy used for the approved, created role.
- `scripts/runtime-check.sh`: version/presence check to run inside the real Harness delegate.
- `changelog/db.changelog-master.yaml`: includes only the first collection changeset.
- `changelog/changes/002-create-index.yaml`: inactive incremental exercise.
- `changelog/changes/003-seed-data.yaml`: inactive native-executor exercise; blocked on safe credential handling.
- `docs/versions.md`: artifact evidence and unresolved compatibility checks.
- `docs/resource-inventory.md`: distinguishes handoff evidence from live verification.

## Local checks

Run from this repository on the Mac mini:

```bash
bash -n scripts/runtime-check.sh
ruby -rpsych -e 'Dir.glob("changelog/**/*.yaml").each { |p| Psych.safe_load(File.read(p), aliases: false); puts "YAML syntax OK: #{p}" }'
python3 -m json.tool infra/aws/launch-plan.json > /dev/null
python3 -m json.tool infra/aws/ec2-trust-policy.json > /dev/null
git diff --check
```

These check syntax only. They do not validate Liquibase change semantics, connect to Atlas, test credentials, or establish migration safety.

## Execution sequence

1. Restore browser access and inspect actual AWS, Harness, and Atlas resources.
2. Verify free entitlements, final costs, network routing, and the current launch proposal.
3. Complete the required action-time confirmation for the SSM role when the console action is prepared.
4. Launch and test SSM plus a shutdown safeguard, then build/register the pinned delegate.
5. Validate Java-driver and native-executor credentials independently before any native exercise.
6. Run the master changelog at commit A, inspect the resulting collection/tracking, and rerun A as a no-op.
7. Add the existing 002 path to the master in commit B; run and verify the named index, then test its scoped rollback.
8. After resolving the native credential issue, add 003 in a later commit and verify/reverse its synthetic fixtures.

Applied changeset IDs, authors, contents, and paths are immutable. Existing 002/003 files are drafts until activated and tested. Do not use `includeAll`: the later exercises must remain inactive until their checkpoints pass.

The detailed planning runbook is currently in the parent task's `outputs/mongodb-liquibase-harness-runbook.md`; an authored copy is also kept at `docs/runbook.md`. Actual versions, installation commands, and acceptance results will replace provisional sections during execution.

## Current execution state

- User approved the $20 monthly AWS lab limit. Instance `i-0635332c43aa733a5` is deployed with the approved SSM role. A 45-second test stopped the instance; restart restored remote access and the normal two-hour timer. See [the console checkpoint](docs/aws-console-checkpoint.md).
- The expired GitHub CLI sign-in was renewed through the user’s browser authorization. Private repository: `Korrojo/mongodb-liquibase-harness-lab`; publish only the setup branch.
- The actual saved Harness runtime pipeline is exported in `.harness/runtime-check.yaml`; it has not run.
- Sixteen candidate runtime libraries resolved successfully. A password-redaction defect was reproduced with synthetic data and repaired in a pinned source patch. See [the offline runtime checkpoint](docs/runtime-offline-checkpoint.md).
- The Linux custom image is built and its tool checks, extension probe, and driver failure-redaction check pass. See [the Linux runtime checkpoint](docs/linux-runtime-checkpoint.md). Delegate registration and real Atlas validation remain pending.
- Native-executor credential handling remains unfinished. Exercise 003 stays inactive.

The full lab is not yet a tested installation recipe. Each unverified stage remains explicitly pending.
