FROM us-docker.pkg.dev/gar-prod-setup/harness-public/harness/delegate@sha256:20fe5d8149b973b0839350bee2ea61a7e57534c2ca3c260979e216e9b499109f
USER root
COPY runtime-libs/ /opt/mongodb-lab/lib/
COPY extension.jar /opt/mongodb-lab/lib/harness-mongodb-lab.1.jar
COPY mongosh/ /opt/mongodb-lab/mongosh/
COPY inputs/infra/delegate/liquibase /usr/local/bin/liquibase
RUN chmod 0755 /usr/local/bin/liquibase \
    && ln -s /opt/mongodb-lab/mongosh/bin/mongosh /usr/local/bin/mongosh \
    && mkdir -p /opt/mongodb-lab/work /opt/mongodb-lab/locks \
    && chown 1001:0 /opt/mongodb-lab/work /opt/mongodb-lab/locks
USER 1001
