#!/usr/bin/env python3
"""Exercise the compiled trusted parser against real and adversarial changelog trees."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

repo = Path(__file__).resolve().parents[1]
classpath = sys.argv[1]
passed = 0


def check(name, mutate, expected):
    global passed
    with tempfile.TemporaryDirectory(prefix="mongodb-pr-check-") as directory:
        candidate = Path(directory) / "candidate"
        shutil.copytree(repo / "changelog", candidate / "changelog")
        mutate(candidate)
        result = subprocess.run(
            ["java", "-cp", classpath, "LabChangelogCheck", str(repo), str(candidate)],
            env={key: value for key, value in os.environ.items()
                 if not any(secret in key.upper() for secret in ("PASSWORD", "TOKEN", "SECRET"))},
            capture_output=True, text=True, timeout=30,
        )
        assert (result.returncode == 0) == expected, f"{name}: unexpected result: {result.stderr}"
        passed += 1
        print(f"PASS {name}")


def append_change(candidate, contents=None):
    master = candidate / "changelog/db.changelog-master.yaml"
    master.write_text(master.read_text() + "  - include:\n      file: changes/004-example.yaml\n      relativeToChangelogFile: true\n")
    (candidate / "changelog/changes/004-example.yaml").write_text(contents or """databaseChangeLog:
  - changeSet:
      id: lab-004-example
      author: Korrojo
      changes:
        - createIndex:
            collectionName: lab_items
            keys: '{ "labFixture": 1 }'
            options: '{ "name": "lab_fixture_lookup" }'
      rollback:
        - runCommand:
            command: '{ "dropIndexes": "lab_items", "index": "lab_fixture_lookup" }'
""")


check("current three-change baseline", lambda p: None, True)
check("append a new index changeset", append_change, True)
check("reject modified executed migration", lambda p: (p / "changelog/changes/001-create-collection.yaml").write_text("databaseChangeLog: []\n"), False)
check("reject malformed proposed YAML", lambda p: append_change(p, "databaseChangeLog: [\n"), False)
check("reject duplicate YAML keys", lambda p: append_change(p, "databaseChangeLog: []\ndatabaseChangeLog: []\n"), False)
check("reject removal from master", lambda p: (p / "changelog/db.changelog-master.yaml").write_text("databaseChangeLog: []\n"), False)
check("reject native script edit", lambda p: (p / "changelog/scripts/003-seed-data.js").write_text("throw new Error('should not execute');\n"), False)


def symlink_include(candidate):
    append_change(candidate)
    path = candidate / "changelog/changes/004-example.yaml"
    path.unlink()
    path.symlink_to(repo / "changelog/changes/001-create-collection.yaml")


check("reject symlink include outside candidate", symlink_include, False)
check("reject custom code change", lambda p: append_change(p, """databaseChangeLog:
  - changeSet:
      id: custom-code
      author: Korrojo
      changes:
        - customChange:
            class: java.lang.Runtime
"""), False)


def missing_index_keys(candidate):
    append_change(candidate)
    path = candidate / "changelog/changes/004-example.yaml"
    path.write_text(path.read_text().replace('            keys: \'{ "labFixture": 1 }\'\n', ''))


def duplicate_identity(candidate):
    append_change(candidate)
    path = candidate / "changelog/changes/004-example.yaml"
    path.write_text(path.read_text().replace("lab-004-example", "lab-001-create-collection"))


def native_candidate(candidate, outside=False):
    script = "../../../outside.js" if outside else "../scripts/004-never-executed.js"
    append_change(candidate, f"""databaseChangeLog:
  - changeSet:
      id: lab-004-native
      author: Korrojo
      runWith: mongosh
      changes:
        - mongoFile:
            path: {script}
            relativeToChangelogFile: true
""")
    path = candidate.parent / "outside.js" if outside else candidate / "changelog/scripts/004-never-executed.js"
    path.write_text("throw new Error('NATIVE_EXECUTION_MUST_NOT_HAPPEN');\n")


check("reject missing required index keys", missing_index_keys, False)
check("reject duplicate changeset identity", duplicate_identity, False)
check("reject native path escaping checkout", lambda p: native_candidate(p, True), False)
check("inspect native migration without executing its JavaScript", native_candidate, True)
print(f"{passed} offline PR preflight scenarios passed; no database was contacted.")
