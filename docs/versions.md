# Runtime inventory and compatibility findings

Inspected September 6, 2026. No runtime has been built or deployed.

| Component | Evidence | Status |
|---|---|---|
| EC2 OS/architecture | Launch form: Amazon Linux 2023 `2023.12.20260831.0`, kernel 6.18, x86_64, `ami-081b0a6eac00b4f53` | Draft only; not booted |
| Harness base image | Live Docker installer: `us-docker.pkg.dev/gar-prod-setup/harness-public/harness/delegate:26.08.89804` | Tag verified in UI; digest and runtime validation pending |
| Liquibase | 4.33.0 in embedded extension build metadata | Candidate, not integration-tested |
| Harness extension | `io.harness:liquibase-mongodb-dbops-extension:1.0.0-4.33.0` downloaded from vendor repository | Archive and bytecode inspected; not executed |
| MongoDB Java driver | Embedded metadata declares `mongodb-driver-sync:5.5.1` | Full resolved dependency set pending |
| Jackson | Embedded metadata declares a `jackson-core.version` property of 2.15.3 | Effective inherited dependency versions still need resolution |
| Migration Java | EC2 image version not selected | Mini has Temurin 17.0.20+8 arm64; this is not the EC2 runtime |
| mongosh | Not selected | Linux package, checksum, and native behavior pending |

The downloaded release POM has coordinates only; it contains no dependency declarations. The JAR embeds an upstream-style POM with version `4.33.0.1-SNAPSHOT`, Liquibase 4.33.0, and MongoDB driver 5.5.1. Therefore the public artifact coordinate alone does not establish its source revision or resolved runtime dependencies. Verify the actual archive and pin its bytes; do not build from arbitrary `main` or copy the README's older dependency list.

Observed local SHA-256 values (fingerprints of downloaded bytes, not an independently supplied vendor signature):

```text
JAR  044f6f52a4b0a1aa88c1f5e82f6f3428956f90ce583310990579e7ba25596037
POM  696fc7ac935c4273ed9829b9fd6ef94e277e31116da4c07f7738923c3e26095d
```

[Published extension JAR](https://us-maven.pkg.dev/gar-prod-setup/harness-maven-public/io/harness/liquibase-mongodb-dbops-extension/1.0.0-4.33.0/liquibase-mongodb-dbops-extension-1.0.0-4.33.0.jar), [Published POM](https://us-maven.pkg.dev/gar-prod-setup/harness-maven-public/io/harness/liquibase-mongodb-dbops-extension/1.0.0-4.33.0/liquibase-mongodb-dbops-extension-1.0.0-4.33.0.pom).

## Native credential issue to resolve

Read-only disassembly of the downloaded `MongoshRunner` confirms that it adds the database connection string to the child-process argument list. It masks a matching credential pattern in one informational log statement; that masking does not remove credentials from process arguments. A failure path also constructs a message from `getCommandString()`, so comprehensive error redaction needs verification.

The source at the inspected tree revision injects separately supplied username/password values into the stored connection string. This makes passing separate Liquibase credentials insufficient evidence that the native subprocess will be credential-free. We have not performed a live credential-leak test and have not exposed any credential.

Do not enable the native changeset until a version or reviewed implementation passes tests for process arguments, normal logs, failure logs, temporary files, and cleanup. A shell wrapper receiving an already credential-bearing argument would not by itself solve the original exposure. The initial collection/index exercises use driver-backed change types; they still need their own credential/logging checks before execution.

[Inspected native runner source](https://github.com/harness-community/liquibase-mongodb-extension/blob/1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021/src/main/java/liquibase/ext/mongodb/tools/MongoshRunner.java), [Inspected connection source](https://github.com/harness-community/liquibase-mongodb-extension/blob/1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021/src/main/java/liquibase/ext/mongodb/database/MongoConnection.java).

## Validation still required

1. Resolve the complete effective dependencies and eliminate duplicate upstream/fork classes.
2. Verify delegate and migration Java compatibility without replacing the delegate's required Java.
3. Select Linux mongosh and test both driver and native execution in the actual container.
4. Verify custom-image upgrade control and reproducible rebuild.
5. Run the acceptance checks in the runbook before freezing the tested version inventory.

## Local dependency and redaction checks

- Resolved the candidate runtime using `infra/delegate/dependencies.pom.xml`: Liquibase core 4.33.0, picocli 4.7.7, MongoDB driver 5.5.1, Jackson 2.18.2 and transitive libraries. Maven 3.9.16 was downloaded to task outputs and its published SHA-512 verified; no system-wide Maven installation was made.
- `liquibase.integration.commandline.Main --version` passed with Java 17.0.20 on the mini. `scripts/RuntimeProbe.java` loaded exactly one MongoDB database implementation, registered createCollection/createIndex/mongoFile, and parsed all three exercises without opening a connection. These are local JVM checks, not EC2/container or Atlas validation.
- The same probe FAILED its synthetic-password redaction assertion: the released extension returns the entire URI from `MongoConnection.getVisibleUrl()`. This establishes another credential issue independently of the native argv finding. No real database credentials were used. A source fix and offline unit tests are being prepared at pinned upstream commit `1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021`.

The first visible-URL source patch now passes the offline probe. The detailed reproduction and limits are recorded in [runtime-offline-checkpoint.md](runtime-offline-checkpoint.md). Native process-argument exposure remains a separate unresolved item.
