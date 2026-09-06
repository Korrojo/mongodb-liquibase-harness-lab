# Local runtime preparation checkpoint

> Historical checkpoint: this records an earlier phase, including its then-pending work and inputs. It is retained for diagnosis, not as the current run sequence. Atlas authentication, all three migration lessons, scoped rollbacks, invalid-YAML rejection, overlap exclusion and post-restart verification subsequently passed. Use [the current runbook](runbook.md) and [acceptance ledger](validation-results.md). EC2 was left Stopped.

September 6, 2026. These checks ran on the Mac mini with Java 17.0.20. They do not establish Linux container compatibility, Atlas connectivity, or successful migrations.

## Resolved candidate

`infra/delegate/dependencies.pom.xml` selects Liquibase core 4.33.0, its optional CLI dependency picocli 4.7.7, MongoDB driver 5.5.1, and Jackson databind 2.18.2. Maven resolved 16 runtime JARs. Their observed SHA-256 values are in `infra/delegate/runtime-libs.sha256`.

The extension's embedded POM inherits Jackson's effective version through Liquibase parent POM 0.5.8; its separate `jackson-core.version` property is not sufficient evidence for the runtime version. The published Liquibase core POM identifies picocli as optional, so it is explicitly included here. No upstream MongoDB extension or Liquibase commercial extension is added to the candidate runtime classpath.

Sources: [Liquibase core POM](https://repo.maven.apache.org/maven2/org/liquibase/liquibase-core/4.33.0/liquibase-core-4.33.0.pom), [extension parent POM](https://github.com/liquibase/liquibase-parent-pom/blob/v0.5.8/pom.xml).

## Commands used in this workspace

Run from the parent directory containing `lab/` and `outputs/`. Maven 3.9.16 was downloaded from Maven Central into `outputs/`, matched against the published SHA-512, and extracted there. It was not installed system-wide.

```bash
outputs/apache-maven-3.9.16/bin/mvn -B -ntp \
  -f lab/infra/delegate/dependencies.pom.xml \
  -Dmaven.repo.local=outputs/maven-cache \
  org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
  -DoutputDirectory=../../../outputs/runtime-libs -DincludeScope=runtime

java -cp 'outputs/runtime-libs/*:outputs/harness-extension-1.0.0-4.33.0.jar' \
  liquibase.integration.commandline.Main --version

java -cp 'outputs/runtime-libs/*:outputs/harness-extension-1.0.0-4.33.0.jar' \
  lab/scripts/RuntimeProbe.java lab
```

The first two commands passed. The probe loaded exactly one MongoDB implementation, registered the three required change types, and parsed the three changelogs. It then **failed** its synthetic-password redaction assertion on the published Harness artifact. Do not call the original artifact safe based only on a successful version command.

## Visible-URL repair

The source checkout is pinned at Harness community commit `1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021`. `infra/delegate/patches/001-redact-visible-url.patch` changes `getVisibleUrl()` to display only the scheme, hosts, and database. It omits both user information and query options, which can contain authentication material. The patch includes regression tests for standard URLs, SRV formatting, and absent values.

To reproduce the patch in a fresh source checkout:

```bash
gh repo clone harness-community/liquibase-mongodb-extension outputs/extension-source -- --no-checkout
git -C outputs/extension-source checkout --detach 1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021
git -C outputs/extension-source apply --check ../../lab/infra/delegate/patches/001-redact-visible-url.patch
git -C outputs/extension-source apply ../../lab/infra/delegate/patches/001-redact-visible-url.patch

outputs/apache-maven-3.9.16/bin/mvn -B -ntp \
  -f outputs/extension-source/pom.xml \
  -Dmaven.repo.local=outputs/maven-cache \
  -Dtest=MongoConnectionTest,MongoConnectionVisibleUrlTest test

java -cp 'outputs/extension-source/target/classes:outputs/runtime-libs/*' \
  lab/scripts/RuntimeProbe.java lab
```

The initial SRV regression test unexpectedly depended on a DNS TXT lookup. It was changed to mock the driver's parsed SRV value, keeping the formatting regression test independent of DNS and MongoDB. Record the final test totals in `validation-results.md`.

The final probe passed against the patched compiled classes. This source build is not represented as byte-identical to the vendor JAR. No replacement runtime JAR or Docker image has been packaged yet.

## Remaining native-executor work

The published native runner still places its connection URI in mongosh arguments. The visible-URL patch does not repair that separate behavior. Keep exercise 003 inactive until the actual native execution path passes argument, log, error, temporary-file, and cleanup tests. No real database credentials were used in these checks.
