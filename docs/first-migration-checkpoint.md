# First migration and repeat: prepared execution

> Historical checkpoint: this records an earlier phase, including its then-pending work and inputs. It is retained for diagnosis, not as the current run sequence. Atlas authentication, all three migration lessons, scoped rollbacks, invalid-YAML rejection, overlap exclusion and post-restart verification subsequently passed. Use [the current runbook](runbook.md) and [acceptance ledger](validation-results.md). EC2 was left Stopped.

September 6, 2026. The pipeline and helpers are prepared. **No migration has run.** Atlas authentication must pass first. The expected database assertions below remain to be verified against real Liquibase history.

## Prepared and tested

- Reviewed source commit: `5f5a559e1aa606865b965b357042adf027ffe8ea`, private repository `Korrojo/mongodb-liquibase-harness-lab`, branch `setup/lab-foundation`. The master still includes only 001; its path, author, ID and content are unchanged.
- Pipeline definition: `.harness/first-migration.yaml`, named `mongodb-lab-first-migration`. One Shell Script step selects the dedicated delegate, takes a shared target lock, checks out the requested exact commit, runs the orchestration and cleans its private run directory on normal exit or handled termination. A process/host crash can bypass cleanup.
- `scripts/run-first-migration.sh` runs read-only preflight, Liquibase validate/status/update, database verification, a second update, and repeat verification. Both updates must have zero exits; the final state fingerprint must match. A failure stops the sequence.
- `scripts/LabFirstMigrationProbe.java` refuses unrelated collections, documents, history or indexes. For the first exercise it expects only the collection plus Liquibase metadata, one history entry with the known ID/author/path and a checksum, zero documents, the default `_id_` index and no active database lock. It never edits or repairs state.
- Local Java 17 compilation, Bash parsing and YAML parsing passed. Synthetic raw/URL-encoded password log redaction and whole-log suppression above 1 MiB passed. These do not establish database correctness.
- The helper was compiled with the host JDK against the pinned dependencies and copied into the active delegate. `FIRST_MIGRATION_PROBE_INSTALLED` was observed; `flock` and `timeout` exist. Reinstall this deliberately copied class after container replacement.
- Harness saved the pipeline with Validated and Save disabled. The full saved YAML was copied back from the editor and matched the prepared definition. The pipeline has not been run.
- A second nonblocking lock acquisition against the same lock file was rejected while the first held it; the primitive test printed `SHARED_LOCK_EXCLUSION_PASS`. This is not yet the concurrent Harness-run acceptance test.
- The native candidate's Java-driver negative test passed using `LIQUIBASE_COMMAND_URL`, `LIQUIBASE_COMMAND_USERNAME`, and `LIQUIBASE_COMMAND_PASSWORD` environment variables: expected exit 1, loopback connection failure and no synthetic canary in the captured log. Log: `/opt/mongodb-lab/native-env-failure.log`. This verifies those CLI variables without using real credentials.

The active registered image is still lab.1. The separate native candidate is not promoted, and exercise 003 remains inactive.

## Execute after authentication passes

1. In Harness, rerun `mongodb-lab-atlas-connectivity` with normal preflight. Require `ATLAS_CONNECTIVITY_PASS` and Success. If it fails, correct credentials/network as indicated; do not run the migration to diagnose authentication.
2. In EC2 Session Manager, verify the same delegate is running and read the current stop deadline with `sudo systemctl list-timers --all mongodb-lab-autostop.timer --no-pager`. Do not start this seven-minute step with less than ten minutes remaining. Start a new approved working session if needed; do not silently extend the timer.
3. Fetch and detach `/opt/mongodb-lab/repo` at the source commit above using the approved read-only key and strict host checking. From that checkout run `sudo bash infra/delegate/install-first-migration-probe.sh`; require zero exit and the installation marker.
4. Save `.harness/first-migration.yaml` as an INLINE pipeline in Default Project. Require Validated. Start it with **Skip preflight check unchecked** and `LAB_COMMIT` set to the exact 40-character source commit above. The Atlas password comes from the saved project secret as an environment variable; never paste it into YAML or runtime commit input.
5. Require Harness Success, zero exits for every `LIQUIBASE_STEP`, exactly one expected history entry, and `FIRST_MIGRATION_AND_REPEAT_PASS`. Inspect the two Liquibase update summaries: first applies one changeset on an empty lab; the repeat applies zero. A later repeat of this same first-exercise commit should apply zero in both updates.
6. Record execution ID, commit, image digest, actual history path/checksum, update counts, lock release and temporary-directory cleanup. Confirm only the intended lab namespace was written using its narrowly scoped user. This user cannot inventory unrelated databases, so do not claim a cross-database before/after scan.

The lock covers all scripts using this same path on this one delegate. A separate primitive exclusion check does not prove two complete Harness runs serialize correctly; the actual concurrent-pipeline acceptance test remains pending. Liquibase's own database lock is retained too.

If verification fails after a write, inspect data/history before retrying. Do not clear checksums, delete history, automatically release database locks, or drop the collection. The first changeset has no automated rollback here; later exercises use narrower named-index and fixture reversals.
