# Validation results

Runtime/deployment tests are NOT RUN. The SSM role/profile is created and verified; EC2 remains an unsubmitted draft.

| Check | State |
|---|---|
| Shell/YAML/JSON/JavaScript syntax | PASS on September 6: Bash parser, Ruby Psych, Python JSON parser, and Node syntax checks |
| Browser recovery and console inventory | PASS after user restarted the desktop app; AWS/Atlas/Harness accessible |
| EC2 user-data Bash syntax | PASS; no Linux/cloud execution performed |
| IAM role/profile | PASS: user-approved creation; console success and role/profile identifiers read back; one attached policy verified |
| EC2 form | READY FOR LAUNCH CONFIRMATION: profile selected, full form and subnet ACL reviewed; $20 spending approval pending |
| Access after Mac screen locks | NOT VERIFIED: current AWS browser access succeeds; Locked use support and installed authorization component found, enabled state unconfirmed |
| Master includes only first exercise | PASS: exactly one include, `changes/001-create-collection.yaml` |
| Published extension archive inspection | PASS: archive and native runner bytecode inspected |
| Vendor signature / source provenance match | NOT VERIFIED |
| Candidate runtime dependency resolution | PASS: 16 JARs resolved; Liquibase 4.33.0 version command succeeds with Java 17.0.20 on the mini |
| Published-artifact offline probe | PARTIAL: service discovery and three changelogs parse; synthetic-password assertion FAILS |
| Visible-URL source repair | PASS: patched offline probe; focused suite ran 15 tests, zero failures/errors, one existing skipped test |
| Linux image build | NOT RUN |
| Harness runtime check | NOT RUN |
| Atlas connectivity and credential handling | NOT RUN |
| Initial/repeat/incremental migration | NOT RUN |
| Native migration and rollback | NOT RUN |
| Failure and concurrency checks | NOT RUN |
| Stop/start and shutdown safeguard | NOT RUN |
| GitHub publication | NOT RUN |
