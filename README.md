# MongoDB Liquibase Harness lab

**Core lab verified on September 6, 2026.** Atlas authentication, collection/index/native migrations, repeat/no-op execution, scoped rollback/reapplication, invalid-YAML rejection, concurrent-run exclusion and EC2 restart recovery passed. The instance was left **Stopped**; EBS and Atlas evidence remain.

Architecture: private GitHub → Harness Custom stage/Shell Script → persistent custom Docker delegate on EC2 → Atlas `liquibase_lab`. The Mac mini manages files and signed-in browsers. AWS administration uses the Console and its Session Manager terminal.

- [Step-by-step runbook](docs/runbook.md): resume, secrets, exact commit sequence, lessons, expected results, troubleshooting and shutdown.
- [Build and installation guide](docs/build-and-install.md): AWS selections, scoped Git key, source/image build, token handling and delegate promotion.
- [Execution evidence](docs/validation-results.md): actual Harness links, checksums, failures/fixes and test limits.
- [Resource inventory](docs/resource-inventory.md) and [runtime versions](docs/versions.md).

For a normal later session, start the existing instance, verify timer/health/Connected, and run exercises with `LAB_EXERCISE=verify-final` and `LAB_COMMIT=e9a7bd6ba2d2f19f84d6e42a91d71ae6e6faf6e4`. Stop it when finished. The other cycle modes require the starting phase described in the runbook; do not reset history or fixtures to bypass their guards.

The master includes all three applied changesets. Their paths, IDs, authors, contents and native scripts are immutable. Use historical commits for the lesson sequence and a new forward changeset for subsequent changes. Preserve the private `setup/lab-foundation` branch history; never push directly to `main`.

The two source patches are necessary for the tested credential handling. This lab uses the Harness community extension through Custom stages, not the paid Database DevOps module. Secrets and populated environment files are outside Git and images. Native URI environment access still depends on a trusted dedicated runtime.

The phone-after-screen-lock workflow and a complete fresh-account installation remain unverified. These limitations do not change the completed cloud migration results. Older `*-checkpoint.md` documents are historical observations; the guides and acceptance ledger above describe the current state.
