# Runtime inventory and compatibility findings

Inspected September 6, 2026. The custom Linux image is built and its offline/container tests pass. Harness registration and live Atlas/migration tests remain pending. See [the Linux checkpoint](linux-runtime-checkpoint.md) for the final image and reproduction commands.

| Component | Evidence | Status |
|---|---|---|
| EC2 OS/architecture | Launch form: Amazon Linux 2023 `2023.12.20260831.0`, kernel 6.18, x86_64, `ami-081b0a6eac00b4f53` | Booted; Session Manager and timed stop/restart verified |
| Harness base image | Live Docker installer: `us-docker.pkg.dev/gar-prod-setup/harness-public/harness/delegate:26.08.89804` | Pulled on EC2: `sha256:20fe5d8149b973b0839350bee2ea61a7e57534c2ca3c260979e216e9b499109f`, linux/amd64, user 1001 |
| Liquibase | 4.33.0, current LiquibaseCommandLine entry point | Container version and expected driver-failure path verified; real Atlas connection pending |
| Harness extension | Source rebuilt at pinned commit with the visible-URL repair | Packaged/tested on EC2; vendor JAR retained separately as historical comparison |
| MongoDB Java driver | `mongodb-driver-sync:5.5.1` | Resolved graph hash-checked and driver failure exercised in container |
| Jackson | 2.18.2 from effective parent inheritance | Resolved runtime JARs hash-checked; embedded 2.15.3 property did not determine the effective graph |
| Migration Java | Base delegate has Temurin 17.0.19+10 at `/opt/java/openjdk` | Verified in an unregistered container; no Java replacement in the delegate |
| mongosh | Selected official release 2.10.0, linux-x64 archive | GitHub release API SHA-256 `42034ba0fc9a48fd65ddcc5150b2e9d8a777965019220744baee42ee9669d543`; archive verified and container version check passed; native behavior pending |

The downloaded release POM has coordinates only; it contains no dependency declarations. The JAR embeds an upstream-style POM with version `4.33.0.1-SNAPSHOT`, Liquibase 4.33.0, and MongoDB driver 5.5.1. Therefore the public artifact coordinate alone does not establish its source revision or resolved runtime dependencies. Verify the actual archive and pin its bytes; do not build from arbitrary `main` or copy the README's older dependency list.

Observed local SHA-256 values (fingerprints of downloaded bytes, not an independently supplied vendor signature):

```text
JAR  044f6f52a4b0a1aa88c1f5e82f6f3428956f90ce583310990579e7ba25596037
POM  696fc7ac935c4273ed9829b9fd6ef94e277e31116da4c07f7738923c3e26095d
```

[Published extension JAR](https://us-maven.pkg.dev/gar-prod-setup/harness-maven-public/io/harness/liquibase-mongodb-dbops-extension/1.0.0-4.33.0/liquibase-mongodb-dbops-extension-1.0.0-4.33.0.jar), [Published POM](https://us-maven.pkg.dev/gar-prod-setup/harness-maven-public/io/harness/liquibase-mongodb-dbops-extension/1.0.0-4.33.0/liquibase-mongodb-dbops-extension-1.0.0-4.33.0.pom).

## Native credential issue and local candidate

A subsequent [candidate patch and checkpoint](native-runtime-candidate.md) passes local tests for environment-based connection handoff, output redaction, and temporary-file cleanup. It has not been built or tested on EC2. The observations below still apply to the original vendor artifact and the current `lab.1` image.

Read-only disassembly of the downloaded `MongoshRunner` confirms that it adds the database connection string to the child-process argument list. It masks a matching credential pattern in one informational log statement; that masking does not remove credentials from process arguments. A failure path also constructs a message from `getCommandString()`, so comprehensive error redaction needs verification.

The source at the inspected tree revision injects separately supplied username/password values into the stored connection string. This makes passing separate Liquibase credentials insufficient evidence that the native subprocess will be credential-free. We have not performed a live credential-leak test and have not exposed any credential.

Do not enable the native changeset until a version or reviewed implementation passes tests for process arguments, normal logs, failure logs, temporary files, and cleanup. A shell wrapper receiving an already credential-bearing argument would not by itself solve the original exposure. The initial collection/index exercises use driver-backed change types; they still need their own credential/logging checks before execution.

[Inspected native runner source](https://github.com/harness-community/liquibase-mongodb-extension/blob/1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021/src/main/java/liquibase/ext/mongodb/tools/MongoshRunner.java), [Inspected connection source](https://github.com/harness-community/liquibase-mongodb-extension/blob/1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021/src/main/java/liquibase/ext/mongodb/database/MongoConnection.java).

## Validation still required

1. Register the built delegate and execute the saved Harness runtime-check pipeline.
2. Complete real Atlas authentication and migration acceptance tests.
3. Repair and test native execution credentials, logs, arguments, and temporary-file cleanup.
4. Verify operational upgrade/restart behavior after registration.
5. Complete rollback, repeat, failure, and concurrency checks before labeling the full lab verified.

## Local dependency and redaction checks

- Resolved the candidate runtime using `infra/delegate/dependencies.pom.xml`: Liquibase core 4.33.0, picocli 4.7.7, MongoDB driver 5.5.1, Jackson 2.18.2 and transitive libraries. Maven 3.9.16 was downloaded to task outputs and its published SHA-512 verified; no system-wide Maven installation was made.
- `liquibase.integration.commandline.Main --version` passed with Java 17.0.20 on the mini. `scripts/RuntimeProbe.java` loaded exactly one MongoDB database implementation, registered createCollection/createIndex/mongoFile, and parsed all three exercises without opening a connection. These are local JVM checks, not EC2/container or Atlas validation.
- The same probe FAILED its synthetic-password redaction assertion: the released extension returns the entire URI from `MongoConnection.getVisibleUrl()`. This establishes another credential issue independently of the native argv finding. No real database credentials were used. The source repair and focused unit tests now pass at pinned upstream commit `1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021`.

The first visible-URL source patch now passes the offline probe. The detailed reproduction and limits are recorded in [runtime-offline-checkpoint.md](runtime-offline-checkpoint.md). The native candidate still needs Linux/container and live acceptance before promotion.

## EC2 host and image preparation

Host: Docker 25.0.16, Git 2.50.1, x86_64; root disk had 28 GiB free after Docker install. Base image: Red Hat Enterprise Linux 9.8, user 1001 (`harness`), Java 17.0.19; git/curl/microdnf present, javac absent. The image was inspected using an overridden shell entry point and no network, without registration credentials.

Build inputs: `infra/delegate/Dockerfile`, `prepare-on-ec2.sh`, and `liquibase`. The build completed; the CLI wrapper correction and additional container tests are recorded in the Linux checkpoint. The wrapper retains the delegate Java path. Source packaging uses the pinned commit and visible-URL patch; the native credential issue remains unresolved.
