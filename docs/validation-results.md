# Verified lab acceptance — September 6, 2026

**Core lab acceptance PASS.** The final EC2 stop was confirmed with the actual Refresh instances control; the public IPv4 field returned to empty. The encrypted EBS volume, private Git history, runtime images and Atlas evidence are retained. No termination or database reset was performed.

The remote phone-after-screen-lock test remains **NOT VERIFIED**, as does a complete fresh-account installation. These are distinct from the completed cloud migration tests.

## Successful Harness executions

All runs used Default Project, normal preflight, selector `mongodb-lab`, the dedicated EC2 container and the scoped Atlas lab user. Times are UTC on September 6, 2026. Pipeline links lead directly to retained execution evidence.

| Test | Evidence | Result |
|---|---|---|
| Runtime | [Runtime Build 1](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabruntimecheck/executions/Zub2NiznRI-qvumpkMakhA/pipeline), about 19:48 | Git 2.52.0, Temurin 17.0.19+10, Liquibase 4.33.0, mongosh 2.10.0 inside delegate |
| Java-driver Atlas authentication | [Connectivity Build 3](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabatlasconnectivity/executions/g2MSjwSfQqmoIVKpkEgtKg/pipeline), 22:13:08 | Authenticated TLS ping/read; collections=0 before migration |
| First update and repeat | [First migration Build 2](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabfirstmigration/executions/4m9alOMuRIC9Bf8Ob3f6UQ/pipeline), 22:19:35 | `FIRST_MIGRATION_AND_REPEAT_PASS`; 001 history, collection empty, repeat unchanged |
| Native authenticated read | [Native connectivity Build 1](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabnativeconnectivity/executions/7mG4zz1DRGKRccbTXGmZLQ/pipeline), 22:27:49 | Live native read; argument/script/log checks and cleanup passed |
| Index cycle | [Exercises Build 1](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabexercises/executions/xr_R6YVwR5GEZh8c_n_v1A/pipeline), 22:36:15 | Apply 002, repeat unchanged, named-index rollback, phase-1 assertion, reapply to phase 2 |
| Native fixture cycle | [Exercises Build 3](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabexercises/executions/p0R_hqLxRW-h2xEriMqMSw/pipeline), 22:45:47 | Apply 003, repeat unchanged, exact fixture rollback, phase-2 assertion, reapply to phase 3; native temporary files removed and own logs clean |
| Actual invalid-YAML rejection | [Exercises Build 5](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabexercises/executions/BJvDz8rqS4Gf4hc5wCLkNw/pipeline), 22:54:30 | Required parser-specific failure; no update invoked; phase-3 fingerprint unchanged |
| First overlapping run | [Exercises Build 6](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabexercises/executions/zdyrhxVxSOWNapMgnbzPvw/pipeline), 22:56:38 | Lock held from 22:55:43; no-op completed and state unchanged |
| Second overlapping run | [Exercises Build 7 — expected failure](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabexercises/executions/f4xYkgjYQsixW9evVSAuwQ/pipeline), 22:55:59 | `LAB_BUSY`, exit 75 before checkout while first owned lock. Expected FAILED status is the acceptance result |
| Post-restart verification | [Exercises Build 8](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabexercises/executions/75VELdR4QUKSduyD8JejnQ/pipeline), 23:03:01 | After an actual stop/start of the same instance: validate/update exit 0, three previously run, `FINAL_NOOP_PASS` |

Exact source inputs: first `19f335da99e7079961a07f623db09acac5ec7a45`; index `5ba7cd478b547a09957b59bfe37e58733f54e88f`; native `232adee3f6477edd14dbeed6a828a7366d19862d`; corrected validation/concurrency/restart `e9a7bd6ba2d2f19f84d6e42a91d71ae6e6faf6e4`. The later documentation commit does not change these tested execution inputs.

## Final database evidence

Atlas Data Explorer independently showed **3 documents** in `lab_items` and **3 EXECUTED history rows** in `DATABASECHANGELOG`. The helper additionally asserted exact collection names, fixture fields, index definitions, history identities/checksums, the unique tracking-history index, and no active database lock.

| History ID | File | Checksum | Last application UTC |
|---|---|---|---|
| `lab-001-create-collection` | `changelog/changes/001-create-collection.yaml` | `9:f0c70db60b21f992f9956514ab43487f` | 22:19:24.229 |
| `lab-002-create-index` | `changelog/changes/002-create-index.yaml` | `9:3a1386ac0c4bddf0bc0f60641c9e8e10` | 22:36:13.294 |
| `lab-003-seed-data` | `changelog/changes/003-seed-data.yaml` | `9:82966cf0ce48b4a7938593257df2e2b2` | 22:45:45.163 |

All authors are `Korrojo`. The 002/003 times reflect reapplication after their scoped rollback tests. Applied source/identity was not modified. Before correcting 003's missing `runWith`, Atlas explicitly showed only 001/002 history and zero fixture documents.

Final fixtures: `lab-001`/`LAB-001`/Synthetic notebook; `lab-002`/`LAB-002`/Synthetic pencil; `lab-003`/`LAB-003`/Synthetic folder. Each carries `labFixture=mongodb-liquibase-harness`. App indexes are `_id_` and unique `lab_sku_unique` on `{sku:1}`.

Observed phase-2 fingerprint: `c771918bc797a40404c2c445de4203c086f2a8d882bc22ffa498f9c3dcca23aa`. Observed phase-3 fingerprint: `42f0ab66c1442857dbae4290aab3c8391afcf70876e837ac8590f51c85224e7a`. Repeat, invalid-input, concurrency and restart helpers compare full state before/after within each run; they do not force a hardcoded historical checksum into MongoDB.

## Infrastructure, runtime and security evidence

- IAM role/profile and one EC2 instance created under the user's scoped approval and $20 monthly pre-credit limit. Zero inbound security-group rules; encrypted 30 GiB gp3; SSM access; no SSH key pair.
- Actual 45-second autostop test reached Stopped, restarted and restored the normal two-hour timer. Later natural timeout also stopped the instance while credentials were pending.
- Final recovery test: same instance reached Stopped, restarted at **23:01:14 UTC**, address `34.231.255.239`, new timer deadline **2026-09-07 01:01:22 UTC**. SSM session `lab-admin-f23ysubsic3phi8958a4uj3az8` worked; native image, read-only key mount and helper classes persisted. Health was briefly unhealthy during startup, later explicitly healthy. `RECOVERY_HEALTH_AND_CLEANUP_PASS` was observed before the final graceful stop.
- Final AWS refresh confirmed **Stopped** and no public IPv4. The deadline above is historical for that stopped boot; read the new one on the next start.
- Active image before final stop: `sha256:dbbe662baa881398d88262981262c74f325e5bb5bc5b8cf911e8ad3781bfb663`; native JAR SHA-256 `07dc953dea798763ab530f62dd8250a48991225729de13f8ad9d90e5c512674a`.
- Native source suite: 62 focused tests on Mac mini and Linux, 61 passed, one existing DNS-related skip, zero failures/errors. Runtime service discovery, changelog parsing, version/dependency checks, synthetic driver failure and real mongosh isolated negative tests passed. These are bounded tests, not a comprehensive security audit.
- Approved repository deploy key verified **Read-only**, strict official GitHub host key pin, private exact-commit fetch as UID 1001. No personal GitHub token was sent to EC2.
- Secrets stored outside Git/image: root-owned mode-0600 delegate token file, mode-0600 repository key with read-only mount, project-scoped Harness Atlas secret. Credentials were never printed or included in this ledger.
- Actual native Atlas path verified no complete URI/password in child arguments/generated script; temporary script 0600 and removed; nonempty mongosh-owned logs credential-free. Synthetic negative-path tests also passed. Environment variables remain readable to sufficiently privileged processes.
- Per-run directories were empty and the target lock acquirable before restart and after final verification. The normal-exit/handled-termination cleanup is not proof against host crashes or SIGKILL.

## Earlier failed or insufficient attempts retained for diagnosis

| Attempt | Finding | Resolution / scope |
|---|---|---|
| Atlas Builds 1/2: `GumjY7_tTfGHoNC83rzESQ`, `wJBKSQepS6eXm1E6xOlt6g` | MongoSecurityException | User updated Atlas and matching Harness secret; Build 3 authenticated |
| First migration Build 1: `y54Hy-ojRXK2pP-eRRMvsg` | Tracking `collMod` denied, error 8000; no app changeset executed | Set `LIQUIBASE_MONGODB_SUPPORTS_VALIDATOR=false`, retain tracking-index adjustment; no role broadening |
| Native exercises Build 2: `ZhPDFbZtTWqc9beIjQXIZA` | Missing `runWith: mongosh`, wrong executor | Verified 003 unapplied, corrected it, Build 3 completed; no checksum clearing/history edits |
| Validation exercises Build 4: `wzg9LHdNSqes579i2CR-Rw` | Harness Success, but only a missing-file rejection | Not accepted as YAML-validation evidence; corrected search path and parser assertion; Build 5 passed |

## Remaining limits

No phone-after-screen-lock acceptance, fresh-account replay, managed Atlas backup, restricted Atlas network allowlist, multi-delegate distributed-lock test, arbitrary-crash recovery or production suitability claim. Existing Atlas broad access rules were unchanged. The real lab user's permission boundary restricts writes to `liquibase_lab`; it does not allow a full cross-database before/after inventory.
