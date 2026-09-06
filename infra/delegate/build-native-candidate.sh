#!/bin/bash
# Run as root from the reviewed lab checkout on the existing Linux EC2 host.
# Builds a separate image; does not register it or change the active delegate.
set -Eeuo pipefail
test "$(id -u)" -eq 0
test "$(uname -m)" = x86_64
systemctl is-active --quiet mongodb-lab-autostop.timer
LAB_REPO=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
LAB_TOOLS=/opt/mongodb-lab/build-001
LAB_BUILD=/opt/mongodb-lab/build-native-001
test ! -e "$LAB_BUILD"
umask 027
mkdir "$LAB_BUILD"
cd "$LAB_BUILD"
cp -a "$LAB_REPO" inputs
git clone --quiet https://github.com/harness-community/liquibase-mongodb-extension.git source
git -C source checkout --quiet --detach 1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021
for LAB_PATCH in 001-redact-visible-url.patch 002-native-environment.patch; do
    git -C source apply --check "$LAB_REPO/infra/delegate/patches/$LAB_PATCH"
    git -C source apply "$LAB_REPO/infra/delegate/patches/$LAB_PATCH"
done
"$LAB_TOOLS/apache-maven-3.9.16/bin/mvn" -B -ntp -f source/pom.xml \
    -Dmaven.repo.local="$LAB_TOOLS/maven-cache" \
    -Dtest=MongoshRunnerSecurityTest,MongoshRunnerTest,MongoshFileCreatorTest,MongoshChangeTest,MongoshFileChangeTest,MongoshStatementTest,MongoshExecutorTest,MongoshGeneratorTest,MongoConnectionTest,MongoConnectionVisibleUrlTest \
    -Dlab.mongosh="$LAB_TOOLS/mongosh/bin/mongosh" package > maven-test.log 2>&1
cp source/target/liquibase-mongodb-4.33.0.1-SNAPSHOT.jar extension.jar
cp -a "$LAB_TOOLS/runtime-libs" runtime-libs
(cd runtime-libs && sha256sum -c "$LAB_REPO/infra/delegate/runtime-libs.sha256")
cp -a "$LAB_TOOLS/mongosh" mongosh
cp "$LAB_REPO/infra/delegate/Dockerfile" Dockerfile
cat > .dockerignore <<'IGNORE'
*
!Dockerfile
!runtime-libs/
!runtime-libs/**
!extension.jar
!mongosh/
!mongosh/**
!inputs/
!inputs/infra/
!inputs/infra/delegate/
!inputs/infra/delegate/liquibase
IGNORE
docker build -t mongodb-lab-delegate:lab.native-candidate .
mkdir -p validation/classes
javac -cp 'extension.jar:runtime-libs/*' -d validation/classes \
    inputs/scripts/RuntimeProbe.java inputs/scripts/NativeFailureProbe.java
docker run --rm --network none --entrypoint /opt/java/openjdk/bin/java \
    --mount "type=bind,src=$LAB_BUILD/validation/classes,dst=/validation/classes,readonly" \
    --mount "type=bind,src=$LAB_BUILD/inputs,dst=/validation/repo,readonly" \
    mongodb-lab-delegate:lab.native-candidate \
    -cp '/opt/mongodb-lab/lib/*:/validation/classes' RuntimeProbe /validation/repo > validation/runtime.log 2>&1
docker run --rm --network none --entrypoint /bin/bash \
    --mount "type=bind,src=$LAB_BUILD/validation/classes,dst=/validation/classes,readonly" \
    -e HOME=/tmp/mongodb-native-validation mongodb-lab-delegate:lab.native-candidate -c '
      set -eu
      mkdir -p "$HOME"
      mongosh --version
      /opt/java/openjdk/bin/java -cp "/opt/mongodb-lab/lib/*:/validation/classes" NativeFailureProbe
      test -n "$(find "$HOME/.mongodb/mongosh" -type f -print -quit)"
      if grep -R -F LAB_NATIVE_CONTAINER_CANARY_916 "$HOME/.mongodb/mongosh" >/dev/null; then
        printf "%s\n" "Synthetic credential found in mongosh logs" >&2
        exit 1
      else
        LAB_LOG_SCAN=$?
        test "$LAB_LOG_SCAN" -eq 1
      fi
      printf "%s\n" NATIVE_MONGOSH_LOGS_CLEAN
    ' > validation/native.log 2>&1
if grep -F LAB_NATIVE_CONTAINER_CANARY_916 validation/native.log >/dev/null; then
    printf '%s\n' 'Synthetic credential found in captured native output' >&2
    exit 1
else
    LAB_LOG_SCAN=$?
    test "$LAB_LOG_SCAN" -eq 1
fi
docker image inspect mongodb-lab-delegate:lab.native-candidate --format 'NATIVE_IMAGE={{.Id}} USER={{.Config.User}}'
sha256sum extension.jar
printf '%s\n' 'NATIVE_CANDIDATE_BUILD_PASS'
