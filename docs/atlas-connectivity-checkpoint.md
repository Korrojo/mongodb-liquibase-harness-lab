# Atlas authenticated connectivity checkpoint

September 6, 2026: the first Harness read-only probe failed at authentication. No collection or document was created. The username, authentication database and cluster restriction were checked against the saved Atlas user; the saved password needs correction or confirmation.

| Item | Verified value |
|---|---|
| Cluster | Cluster0, `cluster0.okiw7qi.mongodb.net` |
| Database / user / authentication database | `liquibase_lab` / `liquibase_lab_user` / `admin` |
| Atlas role and scope | `readWrite @ liquibase_lab`, Cluster0 only |
| Harness secret | Project `atlas_password`, encrypted Text, harnessSecretManager |
| Pipeline | `mongodb-lab-atlas-connectivity`, `.harness/atlas-connectivity.yaml` |
| Probe source | `scripts/AtlasConnectivityProbe.java` |
| First build | 1, execution `GumjY7_tTfGHoNC83rzESQ`, FAILED |
| Failure | `ATLAS_CONNECTIVITY_FAIL: MongoSecurityException`, exit 1, at 20:23:21 UTC |

[First execution](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabatlasconnectivity/executions/GumjY7_tTfGHoNC83rzESQ/pipeline).

## Reproduce the check

1. From EC2 Session Manager, fetch and detach `/opt/mongodb-lab/repo` at reviewed commit `c3b66a9bf4317676afbe2f9bc22db451f5eb0846`, using the approved read-only Git key and strict host checking. The delegate and stop timer must be running.
2. From that checkout run `sudo bash infra/delegate/install-connectivity-probe.sh`. The host JDK compiles the helper against the pinned driver libraries; the container has a JRE, so source compilation is deliberately done on the host. Require `CONNECTIVITY_PROBE_INSTALLED` and zero exit.
3. Create the named INLINE Harness pipeline from `.harness/atlas-connectivity.yaml`. Save and require Validated. The step selects `mongodb-lab` and receives the project secret through an environment variable. The script itself contains no credential expression or password argument.
4. Run with **Skip preflight check unchecked**. The probe requires TLS with hostname validation, applies a separate admin-auth credential, pings the lab database and lists collection names without writing them to logs. It emits only the collection count and a PASS marker after both operations succeed.
5. Require `ATLAS_CONNECTIVITY_PASS` and Harness Success. A runtime/version check alone is insufficient. This probe never creates a database or migration history.

The helper class is copied into the container at `/opt/mongodb-lab/probes`; rerun the installation after replacing the container. It is not yet embedded in the image.

## Authentication repair handoff

The Atlas Edit User form and Harness Edit Encrypted Text form are prepared for the user. Save the exact database-user password into `atlas_password`, without surrounding quotes or URL encoding. If the password is uncertain, set a new password for this lab user in Atlas and save the same value in Harness. Do not use the Atlas website login password or a complete connection string. Keep all credentials out of chat and Git. After the user saves it, rerun the existing read-only pipeline before any migration.

The first error is consistent with mismatched credentials; the password has not been retrieved or compared. Do not claim a confirmed mismatch until a corrected value succeeds. Atlas's pre-existing global access-list entry remains unchanged; this test does not establish a restricted network allowlist.

## Retry after Atlas update

The user reported updating the Atlas password. The Atlas edit form was closed; Harness still showed its Edit Encrypted Text form with an empty new-value field. A retry using the existing saved Harness secret also failed with MongoSecurityException at 20:37:19 UTC. Build 2 execution: `wJBKSQepS6eXm1E6xOlt6g`. The user was asked to paste the newly saved database password into Harness Secret Value and click Save. That save and a passing retry remain unverified.

[Second execution](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabatlasconnectivity/executions/wJBKSQepS6eXm1E6xOlt6g/pipeline).
