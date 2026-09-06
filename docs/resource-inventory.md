# Resource inventory

Updated September 6, 2026. Account identifiers remain in the private source handoff; populate actual resource IDs only after inspection.

| Resource | Status | Evidence |
|---|---|---|
| GitHub owner | Connected plugin identified `Korrojo` | Read in this execution phase |
| Local repository | Created in this task's `lab/` directory | Branch `setup/lab-foundation`; remote not configured |
| Remote repository | Not established | Publication pending |
| AWS account resources | Not inspected in this phase | Browser policy verification failed before access |
| EC2 instance | Handoff reports none created | Must verify before launch |
| IAM role/profile | Handoff reports pending | Draft files exist locally only |
| Harness runtime-check pipeline | Present in Default Project during prior review | UI showed Validated; not executed |
| Harness delegate | Handoff reports none registered | Must inspect current inventory |
| Atlas cluster | An open tab was titled Cluster0 Data | Page contents, tier, users, and network access unverified |
| `liquibase_lab` database | Unknown | No connection or migration made |
| Phone and MacBook fallback | Not tested by this task | Browser setup was supplied by the user |

The tab title is navigation evidence, not proof of a healthy cluster or successful authentication.
