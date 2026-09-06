#!/usr/bin/env bash
set -euo pipefail

# Run in the Harness Shell Script step On Delegate, not on the Mac mini.
printf 'MongoDB lab runtime check\n'
for tool in git java liquibase mongosh; do
  command -v "$tool" >/dev/null || {
    printf 'Missing required tool: %s\n' "$tool" >&2
    exit 1
  }
done

git --version
java -version
liquibase --version
mongosh --version
printf 'Runtime tools available. Extension loading and database connectivity have not been tested.\n'
