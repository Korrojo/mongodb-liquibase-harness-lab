# Resource inventory

Updated September 6, 2026 after the desktop app restart restored normal browser access. The IAM role/profile has now been created with user approval; EC2 remains an unsubmitted draft.

| Resource | Status | Evidence |
|---|---|---|
| GitHub owner | Connected plugin identified `Korrojo` | Read in this execution phase |
| Local repository | Created in this task's `lab/` directory | Branch `setup/lab-foundation`; remote not configured |
| Remote repository | Not established | Publication pending |
| AWS account | Learning-account `224772450208`, `lab-admin`, `us-east-1` | Console Home; $100 credit balance, $0 current-month spend, free-plan end January 31, 2027 |
| EC2 instances and EBS volumes | None in `us-east-1` | Both unfiltered console lists were empty |
| IAM role/profile | CREATED and verified: `mongodb-lab-ec2-ssm` | Role `arn:aws:iam::224772450208:role/mongodb-lab-ec2-ssm`; profile `arn:aws:iam::224772450208:instance-profile/mongodb-lab-ec2-ssm`; one policy, `AmazonSSMManagedInstanceCore`; profile selected in the EC2 launch draft |
| Security groups | One default group | `sg-028be4f0e0d3ca998`; no lab group exists |
| VPC | Default VPC, available; DNS resolution and DNS hostnames enabled | `vpc-0d7b3e0e5bffb47b7`, `172.31.0.0/16` |
| Selected subnet | Draft selects `us-east-1a` | `subnet-09c6def06208373b3`, `172.31.0.0/20`; implicitly associated with the main route table |
| Subnet network ACL | `acl-076f802dc0142516a` | Selected subnet association verified; inbound and outbound rule 100 allow all IPv4 traffic, followed by default deny; no ACL edits |
| Internet route | Active default route through internet gateway | `rtb-0c1c96972f256d684`: `0.0.0.0/0` to `igw-0ed399e4a94494fe5` |
| Harness runtime-check pipeline | Present in Default Project during prior review | UI showed Validated; not executed |
| Harness delegate | No project delegates; Docker option available | Installer offers base image tag `26.08.89804`; no container installed or registered |
| Atlas cluster | `Cluster0`, project `PROJECT_01`, FREE, MongoDB 8.0.32 | AWS N. Virginia, three-node replica set, 330.02 MB of 512 MB shown; backups inactive |
| Atlas database users | One existing SCRAM user: `admin`, `atlasAdmin @ admin`, all resources | A separate restricted lab user still needs creation |
| Atlas network access | Six active rules, including `0.0.0.0/0` | Existing rules untouched; adding a `/32` alone would not narrow access while the broad rule remains |
| `liquibase_lab` database | Not in the current database list | No connection or migration made by this task |
| Phone and MacBook fallback | Current browser access works; access after screen lock is not verified | Locked use setting state still unconfirmed |

The cloud console inventory does not replace a runtime connectivity test. No other region was inventoried. See [the console checkpoint](aws-console-checkpoint.md) for the prepared settings and next steps.
