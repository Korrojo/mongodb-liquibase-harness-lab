# Linux runtime build and acceptance checkpoint

Verified September 6, 2026 on EC2 `i-0635332c43aa733a5`. The image is built but **not registered with Harness**, and no Atlas connection or migration has run.

## Exact runtime

| Component | Verified value |
|---|---|
| Host | AL2023 x86_64; Docker 25.0.16; Git 2.50.1 |
| Delegate base | `us-docker.pkg.dev/gar-prod-setup/harness-public/harness/delegate:26.08.89804` |
| Base digest | `sha256:20fe5d8149b973b0839350bee2ea61a7e57534c2ca3c260979e216e9b499109f` |
| Container OS/user | RHEL 9.8; UID 1001 (`harness`), GID 0 |
| Container Java | Temurin 17.0.19+10 at `/opt/java/openjdk`; retained from Harness |
| Liquibase | 4.33.0; current `LiquibaseCommandLine` entry point |
| Extension source | `1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021` plus tracked visible-URL patch |
| Built extension SHA-256 | `05169bc6ba81a7a2a5e1f0c0ebf2bb4e968fee3a969ca022cf69562d16426b0f` |
| Libraries | Sixteen JARs checked against `infra/delegate/runtime-libs.sha256` |
| mongosh | 2.10.0 linux-x64, published archive digest verified |
| Final image tag | `mongodb-lab-delegate:lab.1`, stored on this EC2 host |
| Final image ID | `sha256:af90951e01a6960893072359c0b0d2c6ae2a2f7fe77df9651ea807ac540904f0` |

The image ID is a local build fingerprint, not a registry pull digest. Rebuilding can yield different image/JAR hashes because the build embeds metadata; record each result. Pin the inputs and verify behavior. No image has been published to a container registry.

## Reproduce the host and build

Run these commands only in the EC2 Session Manager terminal. The user-data bootstrap and stop/restart acceptance steps are in [the AWS checkpoint](aws-console-checkpoint.md).

```bash
sudo systemctl is-active mongodb-lab-autostop.timer
sudo dnf -y install docker git
sudo systemctl enable --now docker
sudo docker version --format 'Docker server {{.Server.Version}}'
git --version
```

The recorded build directory is `/opt/mongodb-lab/build-001`. It must be fresh and contain an `inputs/` directory holding this repository's `infra/delegate/`, `scripts/RuntimeProbe.java`, and `changelog/`. Do not include account credentials. The initial transfer used a compressed, credential-free bundle, verified its SHA-256 before extraction, and ran through the browser terminal. The later private Git checkout route still needs its repository-scoped read credential and a real test.

Put the build and log redirection inside the root shell, as performed in this session:

```bash
sudo bash -c 'cd /opt/mongodb-lab/build-001 && bash inputs/infra/delegate/prepare-on-ec2.sh > build.log 2>&1'
sudo tail -n 3 /opt/mongodb-lab/build-001/build.log
```

Expected final marker: `CUSTOM_RUNTIME_BUILD_PASS`. Read the actual log if the command fails; do not treat a successful download or image tag as a completed build.

The script installs the Java 17 compiler on the host; verifies Maven 3.9.16; checks out the exact extension commit; applies the patch; runs focused tests and packages the extension; resolves and verifies runtime JARs; runs the offline probe; verifies mongosh's archive; builds the custom image; and runs tool version checks as user 1001. The compiler is not added to the final delegate image. The build and download logs contain no real account credentials.

Use `LiquibaseCommandLine` in the wrapper. The older `Main` entry point passed a version check but rejected the modern CLI options during the negative test; replacing that entry point and rebuilding resolved it.

## Checks actually performed

- Focused extension suite on Linux: 15 tests, zero failures/errors, one existing skipped DNS test; therefore 14 passed. This is not the complete upstream test suite.
- Custom container: git, Java, Liquibase 4.33.0, and mongosh 2.10.0 version commands passed without registration or database credentials.
- Compiled `RuntimeProbe` on the host and ran it inside the container with no network and read-only inputs: exactly one MongoDB database implementation, all three required change types, each exercise parsed, synthetic visible-URL password removed.
- Driver negative test: network disabled, loopback port 1, fake username/password in a mode-0600 defaults file. The current CLI reached the driver, returned exit 1 with `MongoTimeoutException`/connection refused, and its captured log did not contain the password canary. Reproduce with `infra/delegate/test-driver-failure.sh` as root from the build directory.

For the container probe:

```bash
sudo mkdir -p validation/classes
sudo javac -cp 'extension.jar:runtime-libs/*' -d validation/classes inputs/scripts/RuntimeProbe.java
sudo docker run --rm --network none --entrypoint /opt/java/openjdk/bin/java \
  -v "$PWD/validation/classes:/validation/classes:ro" \
  -v "$PWD/inputs/changelog:/validation/changelog:ro" \
  mongodb-lab-delegate:lab.1 \
  -cp '/opt/mongodb-lab/lib/*:/validation/classes' RuntimeProbe /validation
```

The driver currently reports that SLF4J is absent, so driver diagnostics use their fallback behavior. These checks do not prove successful authentication, all error paths, or native-process credential handling. Exercise 003 remains inactive.

## Account setup prepared, not completed

Atlas form: `liquibase_lab_user`, SCRAM, specific `readWrite` on `liquibase_lab`, collection left blank, access restricted to `Cluster0`; no broad built-in role selected. User must enter the username/password and submit. Harness Default Project has a prepared encrypted-text form named `atlas_password`; user must enter the same password and Save. Never put it in chat or this repository.

Delegate registration is prepared in `infra/delegate/register-delegate.sh`: one named container, user 1001 from the image, 1 CPU/4 GiB limit, bounded logs, no published ports or Docker socket, and no automatic upgrader. The installer token must be in root-owned `/etc/mongodb-lab/delegate.env`, mode 0600, outside this repository. Action-time registration approval is pending; the saved runtime-check pipeline is unrun.

Sources: [AWS Docker setup](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/create-container-image.html), [Harness custom images and upgrades](https://developer.harness.io/docs/platform/delegates/install-delegates/build-custom-delegate-images-with-third-party-tools/), [mongosh 2.10.0 release](https://github.com/mongodb-js/mongosh/releases/tag/v2.10.0).
