# Validation results

EC2 deployment, management/shutdown, runtime and read-only Git access tests PASS. The separate native candidate also passes Linux/container checks. The first Atlas authentication check FAILED; migration validation remains pending.

| Check | State |
|---|---|
| Shell/YAML/JSON/JavaScript syntax | PASS on September 6: Bash parser, Ruby Psych, Python JSON parser, and Node syntax checks |
| Browser recovery and console inventory | PASS after user restarted the desktop app; AWS/Atlas/Harness accessible |
| EC2 user-data Bash syntax | PASS; cloud-init deployed the units successfully on EC2 |
| IAM role/profile | PASS: user-approved creation; console success and role/profile identifiers read back; one attached policy verified |
| EC2 form | PASS: user approved $20 monthly lab limit; launched once and recorded instance/SG/volume IDs |
| Access after Mac screen locks | NOT VERIFIED: current AWS browser access succeeds; Locked use support and installed authorization component found, enabled state unconfirmed |
| Master includes only first exercise | PASS: exactly one include, `changes/001-create-collection.yaml` |
| Published extension archive inspection | PASS: archive and native runner bytecode inspected |
| Vendor signature / source provenance match | NOT VERIFIED |
| Candidate runtime dependency resolution | PASS: 16 JARs resolved; Liquibase 4.33.0 version command succeeds with Java 17.0.20 on the mini |
| Published-artifact offline probe | PARTIAL: service discovery and three changelogs parse; synthetic-password assertion FAILS |
| Visible-URL source repair | PASS: patched offline probe; focused suite ran 15 tests, zero failures/errors, one existing skipped test |
| Linux image build | PASS: custom image built on EC2, Java 17/Liquibase 4.33.0/mongosh 2.10.0 verified as user 1001 |
| Container extension probe | PASS: one provider, required change types, three parsed exercises, visible-URL redaction |
| Driver failure logging | PASS after correcting the CLI entry point: expected loopback connection failure, exit 1, synthetic password absent from captured logs |
| Harness runtime check | PASS: Build 1, execution Zub2NiznRI-qvumpkMakhA, selected connected delegate mongodb-lab; Git 2.52.0, Java 17.0.19, Liquibase 4.33.0, mongosh 2.10.0 |
| Atlas connectivity and credential handling | FAILED: Builds 1 and 2 returned MongoSecurityException. After the reported Atlas password update, the matching Harness secret update is still pending. Username/admin authentication database/Cluster0 scope verified; see atlas-connectivity-checkpoint.md |
| Initial/repeat/incremental migration | NOT RUN |
| Native migration and rollback | NOT RUN |
| Native security repair candidate | PASS on mini and Linux: 62 focused tests, zero failures/errors, one existing skip. Separate container passes RuntimeProbe and real mongosh 2.10.0 isolated failure, script cleanup and own-log canary checks. Candidate not promoted; live native Atlas checks NOT RUN |
| Failure and concurrency checks | NOT RUN |
| Stop/start and shutdown safeguard | PASS: actual timer expired using a 45-second runtime override; EC2 Stopped observed; same instance restarted, SSM reconnected, two-hour timer rearmed with no override |
| GitHub publication | PASS: private repository, setup/lab-foundation pushed; GitHub helper configured only in this local repository |
| Private checkout from delegate | PASS: saved Read-only deploy key, key mount RW=false, exact-commit checkout as UID 1001 |
| First migration preparation | PASS for syntax/compilation, log sanitizer, helper installation, saved YAML readback and local lock exclusion. Real migration, repeat and concurrent-pipeline acceptance NOT RUN |
