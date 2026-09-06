# Resource inventory

Updated September 6, 2026 after full migration and restart acceptance. The user approved the current personal lab and a $20 monthly AWS limit before credits. The instance is now **Stopped**; retained EBS and Atlas data remain.

| Resource | Status | Evidence |
|---|---|---|
| GitHub owner | Connected plugin identified `Korrojo` | Read in this execution phase |
| Local repository | Created in this task's `lab/` directory | Branch `setup/lab-foundation`; private origin configured and published |
| Remote repository | PRIVATE `Korrojo/mongodb-liquibase-harness-lab` created | Setup branch published; initial deployment checkpoint `9b7df8f` |
| AWS account | Learning-account `224772450208`, `lab-admin`, `us-east-1` | Initial Console Home snapshot: $100 credits, $0 month spend, plan end January 31, 2027; billing delayed and not rechecked after the lab |
| Lab EC2 instance | `i-0635332c43aa733a5`, `mongodb-lab-delegate` | Initial launch 17:28:26 UTC; final recovery start 23:01:14 UTC; post-restart no-op passed at 23:03:01 UTC; final graceful stop confirmed before 23:08:28 UTC |
| Lab EBS volume | `vol-061b8b79de6fdaa6a` | 30 GiB encrypted root; delete-on-termination Yes; same volume retained after stop |
| IAM role/profile | CREATED and verified: `mongodb-lab-ec2-ssm` | Role `arn:aws:iam::224772450208:role/mongodb-lab-ec2-ssm`; profile `arn:aws:iam::224772450208:instance-profile/mongodb-lab-ec2-ssm`; one policy, `AmazonSSMManagedInstanceCore`; role attached to the launched instance |
| Lab security group | `sg-0b2f11fa130e4facc`, `mongodb-lab-delegate-sg` | Created at launch with zero inbound rules; default group untouched |
| VPC | Default VPC, available; DNS resolution and DNS hostnames enabled | `vpc-0d7b3e0e5bffb47b7`, `172.31.0.0/16` |
| Selected subnet | Instance in `us-east-1a` | `subnet-09c6def06208373b3`, `172.31.0.0/20`; implicitly associated with the main route table |
| Subnet network ACL | `acl-076f802dc0142516a` | Selected subnet association verified; inbound and outbound rule 100 allow all IPv4 traffic, followed by default deny; no ACL edits |
| Internet route | Active default route through internet gateway | `rtb-0c1c96972f256d684`: `0.0.0.0/0` to `igw-0ed399e4a94494fe5` |
| Harness runtime-check pipeline | Build 1 PASS in Default Project | Execution Zub2NiznRI-qvumpkMakhA selected mongodb-lab; runtime versions verified |
| Harness delegate | Registered and Connected after explicit user approval | mongodb-lab, UID 1001, tested native candidate image; recovered after restart; expected disconnected while EC2 is stopped; bounded logs, no ports or Docker socket |
| Atlas cluster | `Cluster0`, project `PROJECT_01`, FREE, MongoDB 8.0.32 | AWS N. Virginia, three-node replica set; initial usage 330.02 MB/512 MB, not a final usage measurement; backups inactive |
| Atlas database users | Existing admin plus saved SCRAM liquibase_lab_user | Lab user: readWrite on liquibase_lab, one cluster; saved Harness secret atlas_password also verified |
| Atlas network access | Six active rules, including `0.0.0.0/0` | Existing rules untouched; adding a `/32` alone would not narrow access while the broad rule remains |
| `liquibase_lab` database | Final phase 3 verified | Three executed history rows and exactly three synthetic fixtures; named unique SKU index and released lock; see validation-results.md |
| EC2 public IPv4 | None while Stopped | Last boot used `34.231.255.239`; re-read after every stop/start; previous address released |
| Phone and MacBook fallback | Current browser access works; access after screen lock is not verified | Locked use setting state still unconfirmed |

The cloud console inventory does not replace a runtime connectivity test. No other region was inventoried. See [the console checkpoint](aws-console-checkpoint.md) for the deployed settings and next steps.

See [the Harness connection checkpoint](harness-connection-checkpoint.md) for saved credentials, successful runtime check, and approved read-only GitHub access with an exact-commit checkout. Atlas authentication succeeded after the matching Harness secret was saved. All three migration lessons and acceptance tests passed; [current execution evidence](validation-results.md) supersedes the historical checkpoints.
