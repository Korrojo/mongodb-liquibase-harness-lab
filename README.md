# MongoDB Liquibase Harness lab

**Core lab verified on September 6, 2026.** Atlas authentication, collection/index/native migrations, repeat/no-op execution, scoped rollback/reapplication, invalid-YAML rejection, concurrent-run exclusion and EC2 restart recovery passed. The instance was left **Stopped**; EBS and Atlas evidence remain.

**Git automation verified September 7:** a checked PR and protected normal merge automatically applied the fixture lookup index through Harness. Four changesets are recorded, three documents remain, and the repeat made no changes. The stale-revision test rejected an obsolete commit before Atlas access.

Architecture: GitHub → Harness Custom stage/Shell Script → persistent custom Docker delegate on EC2 → Atlas `liquibase_lab`. The Mac mini manages files and signed-in browsers. AWS administration uses the Console and its Session Manager terminal.

- [Git automation setup](docs/git-automation.md) and [daily operating procedure](docs/daily-workflow.md).
- [Step-by-step runbook](docs/runbook.md): resume, secrets, exact commit sequence, lessons, expected results, troubleshooting and shutdown.
- [Build and installation guide](docs/build-and-install.md): AWS selections, scoped Git key, source/image build, token handling and delegate promotion.
- [Execution evidence](docs/validation-results.md): actual Harness links, checksums, failures/fixes and test limits.
- [Resource inventory](docs/resource-inventory.md) and [runtime versions](docs/versions.md).

For later work, follow the [daily Git workflow](docs/daily-workflow.md): open a PR, require the Harness check, and merge normally to deploy automatically. GitHub starts the existing EC2 instance; the host drains active jobs before its two-hour shutdown. The old manual phase-three check is historical after changeset 004.

Applied changesets, IDs, authors, paths and native scripts are immutable. Add a new forward changeset for subsequent changes.

The two source patches are necessary for the tested credential handling. This lab uses the Harness community extension through Custom stages, not the paid Database DevOps module. Secrets and populated environment files are outside Git and images. Native URI environment access still depends on a trusted dedicated runtime.

The phone-after-screen-lock workflow and a complete fresh-account installation remain unverified. These limitations do not change the completed cloud migration results. Older `*-checkpoint.md` documents are historical observations; the guides and acceptance ledger above describe the current state.
