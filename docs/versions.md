# Runtime inventory and compatibility findings

September 6, 2026: the repaired native image was promoted and passed real Atlas native execution, scoped rollback/reapplication and post-restart no-op verification. EC2 is now Stopped. [Acceptance evidence](validation-results.md) · [Build instructions](build-and-install.md).

| Component | Verified value |
|---|---|
| Host | Amazon Linux 2023 `2023.12.20260831.0`, x86_64, kernel 6.18, AMI `ami-081b0a6eac00b4f53` |
| Host Docker / Git | 25.0.16 / 2.50.1 |
| Host build Java / Maven | Corretto JDK 17 / Maven 3.9.16, published SHA-512 checked |
| Harness base | `us-docker.pkg.dev/gar-prod-setup/harness-public/harness/delegate:26.08.89804`, pinned digest below |
| Container OS / identity | RHEL 9.8, UID 1001:GID 0 |
| Container Java / Git | Temurin 17.0.19+10 / 2.52.0 |
| Liquibase | 4.33.0; modern `LiquibaseCommandLine` entry point |
| Extension | Pinned Harness community source plus the two reviewed lab patches |
| MongoDB Java driver | 5.5.1 |
| Jackson | 2.18.2 from effective dependency inheritance |
| Other runtime libraries | Sixteen JARs pinned by `infra/delegate/runtime-libs.sha256`; see `dependencies.pom.xml` |
| mongosh | Official 2.10.0 linux-x64 archive; SHA-256 checked |
| Atlas server | FREE cluster, MongoDB 8.0.32, AWS N. Virginia |

## Recorded immutable inputs and output hashes

```text
Upstream source commit:
1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021

Harness delegate base digest:
sha256:20fe5d8149b973b0839350bee2ea61a7e57534c2ca3c260979e216e9b499109f

Native runtime image used for live acceptance:
sha256:dbbe662baa881398d88262981262c74f325e5bb5bc5b8cf911e8ad3781bfb663

Native extension JAR SHA-256:
07dc953dea798763ab530f62dd8250a48991225729de13f8ad9d90e5c512674a

Original lab.1 image, retained stopped in backup containers:
sha256:af90951e01a6960893072359c0b0d2c6ae2a2f7fe77df9651ea807ac540904f0

mongosh 2.10.0 Linux x64 archive SHA-256:
42034ba0fc9a48fd65ddcc5150b2e9d8a777965019220744baee42ee9669d543

Build-recipe snapshot with 001-only master:
4f5706401a58ae5bab9c6bb8bcb120ad08ee959f
```

Active container name: `mongodb-lab`; image tag: `mongodb-lab-delegate:lab.native-candidate`. The extension filename inside it remains `/opt/mongodb-lab/lib/harness-mongodb-lab.1.jar`; its **bytes are the native candidate hash above**. Do not infer its contents from that historical filename. Preserved stopped backups are `mongodb-lab-before-native` and `mongodb-lab-before-git`.

Image/JAR output hashes identify the actual tested build. A fresh build may differ due to metadata or package availability. Pin inputs, record the newly built outputs and repeat acceptance; do not claim deterministic byte reproduction. The two installation/promotion image guards must match the specifically reviewed candidate, as documented in the build guide.

## Why the patched source build was necessary

The separately downloaded public coordinate `io.harness:liquibase-mongodb-dbops-extension:1.0.0-4.33.0` contained an embedded upstream-style POM at `4.33.0.1-SNAPSHOT`. Its standalone POM declared no dependency graph. We therefore resolved the actual pinned source dependencies and checked their hashes instead of relying on an older README example. The original downloaded JAR fingerprint was `044f6f52a4b0a1aa88c1f5e82f6f3428956f90ce583310990579e7ba25596037`; that fingerprint is not an independent vendor signature or proof of its source revision.

Inspection and synthetic tests found that the original visible URL returned credentials and the native runner placed a credential-bearing connection URI in process arguments. Separate Liquibase username/password inputs alone did not fix the native path.

- `001-redact-visible-url.patch` masks visible connection URLs.
- `002-native-environment.patch` hands the native URI through an environment variable, removes it before the user script, uses private 0600 temporary scripts with immediate cleanup, redacts captured output and handles timeout/oversized output.

Focused tests, real isolated mongosh negative checks and the live Atlas probe all passed for the exercised paths. The live native migration/rollback also checked temporary-file removal and nonempty mongosh-owned logs. Root or equivalent OS access can still inspect environments; only trusted reviewed commits execute with this dedicated lab secret.

The tracking-collection setting `LIQUIBASE_MONGODB_SUPPORTS_VALIDATOR=false` is required for the tested readWrite-only role; default tracking-table adjustment stays enabled, so the unique history index remains. The native changeset requires `runWith: mongosh`. Both were verified through actual failed-then-corrected runs without widening Atlas privileges or editing executed changesets.

[Source repository](https://github.com/harness-community/liquibase-mongodb-extension) · [Pinned original runner](https://github.com/harness-community/liquibase-mongodb-extension/blob/1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021/src/main/java/liquibase/ext/mongodb/tools/MongoshRunner.java) · [Harness native examples](https://developer.harness.io/docs/database-devops/concepts/database-devops/concepts/mongodb-command/).
