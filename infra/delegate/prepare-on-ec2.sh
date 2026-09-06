#!/bin/bash
# Run with sudo on AL2023, from a fresh build directory containing inputs/.
# No account credentials belong in this build context or image.
set -Eeuo pipefail
test "$(uname -s)" = Linux
test "$(uname -m)" = x86_64
systemctl is-active --quiet mongodb-lab-autostop.timer
test -f inputs/infra/delegate/dependencies.pom.xml
test ! -e extension-source
dnf -y install java-17-amazon-corretto-devel
LAB_BUILD_DIR="$PWD"
curl -fLsS --retry 2 --max-time 180 https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.tar.gz -o maven.tar.gz
printf '%s  %s\n' '831a8591fe20c8243b1dbe7d71e3244f31d1665b0804b2e825e38cbbe5ce0cafb8338851f90780735568773e0a6cd07bbec107cda0b896b008b861075358b6f6' maven.tar.gz | sha512sum -c -
tar -xzf maven.tar.gz
git clone https://github.com/harness-community/liquibase-mongodb-extension.git extension-source
git -C extension-source checkout --detach 1d2e9bc199ec364fc4e1520fbde5cfaad7b3f021
git -C extension-source apply --check "$LAB_BUILD_DIR/inputs/infra/delegate/patches/001-redact-visible-url.patch"
git -C extension-source apply "$LAB_BUILD_DIR/inputs/infra/delegate/patches/001-redact-visible-url.patch"
"$LAB_BUILD_DIR/apache-maven-3.9.16/bin/mvn" -B -ntp -f extension-source/pom.xml \
    -Dmaven.repo.local="$LAB_BUILD_DIR/maven-cache" \
    -Dtest=MongoConnectionTest,MongoConnectionVisibleUrlTest package
"$LAB_BUILD_DIR/apache-maven-3.9.16/bin/mvn" -B -ntp -f inputs/infra/delegate/dependencies.pom.xml \
    -Dmaven.repo.local="$LAB_BUILD_DIR/maven-cache" \
    org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
    -DoutputDirectory="$LAB_BUILD_DIR/runtime-libs" -DincludeScope=runtime
(cd runtime-libs && sha256sum -c ../inputs/infra/delegate/runtime-libs.sha256)
cp extension-source/target/liquibase-mongodb-4.33.0.1-SNAPSHOT.jar extension.jar
java -cp 'extension.jar:runtime-libs/*' inputs/scripts/RuntimeProbe.java inputs
curl -fLsS --retry 2 --max-time 240 https://github.com/mongodb-js/mongosh/releases/download/v2.10.0/mongosh-2.10.0-linux-x64.tgz -o mongosh.tgz
printf '%s  %s\n' '42034ba0fc9a48fd65ddcc5150b2e9d8a777965019220744baee42ee9669d543' mongosh.tgz | sha256sum -c -
mkdir mongosh
tar -xzf mongosh.tgz --strip-components=1 -C mongosh
cp inputs/infra/delegate/Dockerfile Dockerfile
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
docker build -t mongodb-lab-delegate:lab.1 .
docker run --rm --network none --entrypoint /bin/bash mongodb-lab-delegate:lab.1 \
    -c 'set -euo pipefail; id; git --version; java -version; liquibase --version; mongosh --version'
docker image inspect mongodb-lab-delegate:lab.1 --format 'CUSTOM_IMAGE={{.Id}} USER={{.Config.User}} ARCH={{.Architecture}}'
sha256sum extension.jar
printf '%s\n' 'CUSTOM_RUNTIME_BUILD_PASS'
