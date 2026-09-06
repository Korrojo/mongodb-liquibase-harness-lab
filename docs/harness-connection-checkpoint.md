# Harness connection and desk handoff checkpoint

> Historical checkpoint: this records an earlier phase, including its then-pending work and inputs. It is retained for diagnosis, not as the current run sequence. Atlas authentication, all three migration lessons, scoped rollbacks, invalid-YAML rejection, overlap exclusion and post-restart verification subsequently passed. Use [the current runbook](runbook.md) and [acceptance ledger](validation-results.md). EC2 was left Stopped.

Verified September 6, 2026. The user explicitly approved connecting the EC2 delegate to Harness in chat; no additional desktop confirmation dialog was necessary for that approval.

## Completed

- Atlas now lists `liquibase_lab_user`, SCRAM, `readWrite @ liquibase_lab`, restricted to one cluster. The prepared cluster selection was Cluster0. Harness Default Project now lists encrypted secret `atlas_password`. The user entered and saved the credentials; this task did not display them. Matching passwords and authentication remain to be tested through the runtime.
- Started the same approved instance `i-0635332c43aa733a5`. Its new public address is `3.237.62.82`. SSM, Docker, and the stop timer are active; the current deadline is **2026-09-06 21:39:46 UTC / 5:39:46 PM Eastern**. The earlier $20 monthly limit still applies.
- Stored the existing project installer token in root-owned `/etc/mongodb-lab/delegate.env`, mode 0600, through terminal input with echo disabled. Verified file ownership, permissions, and token-line format without printing its value. Restored terminal echo afterward.
- Started `mongodb-lab` using the settings in `infra/delegate/register-delegate.sh`: the retained `lab.1` image, UID 1001, one CPU, 4 GiB, restart unless-stopped, bounded logs, no inbound ports, no Docker socket or automatic upgrader. Container state was running with zero restarts.
- Harness Default Project now shows **mongodb-lab — Connected**, with selector `mongodb-lab`.
- Ran the existing runtime-check pipeline with normal preflight enabled. **Build 1 succeeded**, selected `mongodb-lab`, and reported Git 2.52.0, Temurin Java 17.0.19+10, Liquibase 4.33.0, and mongosh 2.10.0. The stage duration shown was 21 seconds; tool output was timestamped 19:48:08–19:48:11 UTC.

[Successful Harness execution](https://app.harness.io/ng/account/7WPs0XUoT4CnMpX3j28V4g/all/orgs/default/projects/default_project/pipelines/mongodblabruntimecheck/executions/Zub2NiznRI-qvumpkMakhA/pipeline).

Java writes version information to stderr, which Harness labels ERROR in individual log rows. That did not fail this step; the execution status is Success. The core also reported no Pro license service and continued. This is the community-extension lab.

## Remaining desk actions

### Repository read access: approved and verified

An Ed25519 key pair was generated on EC2. The private key remains on that server; it has not been displayed or added to the image. The user saved the key in GitHub with write access unchecked; the matching fingerprint and Read-only status were verified.

| Field | Prepared value |
|---|---|
| Repository | Private `Korrojo/mongodb-liquibase-harness-lab` |
| GitHub deploy-key title | `mongodb-lab-ec2-readonly` |
| Public-key fingerprint | `SHA256:HGZUrEyXCJo3x7VOxzcXCA/NkSllq0/Tzwh2WuwqEzc` |
| Private-key path on EC2 | `/etc/mongodb-lab/git/id_ed25519`, mode 0600, UID 1001/GID 0 |
| GitHub access | Read-only; **Allow write access remains unchecked** |
| Current state | Saved Read-only key; mount RW=false and exact-commit fetch as UID 1001 verified |

The host ran `infra/delegate/attach-repository-access.sh` from reviewed commit `4eea9aaeace0440b690a1127b4e54eaa1ac994f8`. It preserved the old container as stopped `mongodb-lab-before-git`, recreated `mongodb-lab` with the same hostname/image, and mounted the Git directory with `RW=false`. Harness returned to Connected. Never start both containers.

`scripts/verify-repository-access.sh` passed inside the delegate as UID 1001 at that exact commit; checkout path `/opt/mongodb-lab/work/repository-check.UjUpY4`. The host also has a private checkout at `/opt/mongodb-lab/repo` using the same scoped key. No personal GitHub CLI token was copied to EC2.

GitHub's Ed25519 host entry was copied from [its official SSH fingerprint page](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/githubs-ssh-key-fingerprints) into `/etc/mongodb-lab/git/known_hosts`. Expected host fingerprint: `SHA256:+DiY3wvvV6TuJJhbpZisF/zLDA0zPMSvHdkr4UvCOqU`. Strict host checking must remain enabled for the actual fetch.

### Locked use: user confirmation and phone test pending

On the Mac mini, open **ChatGPT Settings → Computer Use → Locked use**, enable it if needed, and complete any macOS authorization prompt personally. Computer Use cannot operate ChatGPT itself or approve OS security prompts. After enabling it, start a browser-reading request from the phone while the mini's screen is locked and verify that it succeeds. Existing browser access while unlocked is not this test. [Official Locked use instructions](https://learn.chatgpt.com/docs/computer-use#locked-use).

## Continue remotely after these handoffs

1. Preserve the verified read-only repository access. Keep one shared target lock and exact-commit checkout in the migration orchestration.
2. Test Atlas DNS/TLS and authenticated driver connectivity using the Harness secret, without displaying its value. Then run collection creation and repeat/no-op verification in `liquibase_lab`.
3. The separate native candidate now passes Linux and isolated container checks; see the native checkpoint. Promote through controlled replacement and complete live acceptance before activating exercise 003. The registered image still contains only the visible-URL patch.
4. Complete incremental index, scoped rollback, native fixtures/rollback, and failure/concurrency checks; record real results in the runbook.
5. Stop the instance between work sessions and confirm Stopped. The current boot timer is a fallback; EBS storage continues while stopped.

The first Atlas read-only connection attempt reached the authentication step but failed with MongoSecurityException. See [the Atlas checkpoint](atlas-connectivity-checkpoint.md). No database migration has occurred. The runtime check passes; password correction is pending.
