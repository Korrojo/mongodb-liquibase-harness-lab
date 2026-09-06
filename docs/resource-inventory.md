# Resource inventory

Updated September 6, 2026 after the desktop app restart restored normal browser access. These are read-only console observations; prepared forms are not deployed resources.

| Resource | Status | Evidence |
|---|---|---|
| GitHub owner | Connected plugin identified `Korrojo` | Read in this execution phase |
| Local repository | Created in this task's `lab/` directory | Branch `setup/lab-foundation`; remote not configured |
| Remote repository | Not established | Publication pending |
| AWS account | Learning-account `224772450208`, `lab-admin`, `us-east-1` | Console Home; $100 credit balance, $0 current-month spend, free-plan end January 31, 2027 |
| EC2 instances and EBS volumes | None in `us-east-1` | Both unfiltered console lists were empty |
| IAM role/profile | No lab role exists | Roles list contained only three service-linked roles; final `mongodb-lab-ec2-ssm` review prepared, confirmation pending |
| Security groups | One default group | `sg-028be4f0e0d3ca998`; no lab group exists |
| VPC | Default VPC, available; DNS resolution and DNS hostnames enabled | `vpc-0d7b3e0e5bffb47b7`, `172.31.0.0/16` |
| Selected subnet | Draft selects `us-east-1a` | `subnet-09c6def06208373b3`, `172.31.0.0/20`; implicitly associated with the main route table |
| Internet route | Active default route through internet gateway | `rtb-0c1c96972f256d684`: `0.0.0.0/0` to `igw-0ed399e4a94494fe5` |
| Harness runtime-check pipeline | Present in Default Project during prior review | UI showed Validated; not executed |
| Harness delegate | No project delegates; Docker option available | Installer offers base image tag `26.08.89804`; no container installed or registered |
| Atlas cluster | `Cluster0`, project `PROJECT_01`, FREE, MongoDB 8.0.32 | AWS N. Virginia, three-node replica set, 330.02 MB of 512 MB shown; backups inactive |
| Atlas database users | One existing SCRAM user: `admin`, `atlasAdmin @ admin`, all resources | A separate restricted lab user still needs creation |
| Atlas network access | Six active rules, including `0.0.0.0/0` | Existing rules untouched; adding a `/32` alone would not narrow access while the broad rule remains |
| `liquibase_lab` database | Not in the current database list | No connection or migration made by this task |
| Phone and MacBook fallback | Not tested by this task | Browser setup was supplied by the user |

The cloud console inventory does not replace a runtime connectivity test. No other region was inventoried. See [the console checkpoint](aws-console-checkpoint.md) for the prepared settings and next steps.
