#!/usr/bin/env bash
# Run in authenticated AWS CloudShell only after reviewing and approving this grant.
# Use the two JSON files from the same reviewed commit, next to this script.
set -Eeuo pipefail
export AWS_PAGER=''
lab_policy_dir=$(cd -- "$(dirname -- "$0")" && pwd)
test "$(aws sts get-caller-identity --query Account --output text)" = 224772450208
lab_provider_arn='arn:aws:iam::224772450208:oidc-provider/token.actions.githubusercontent.com'
lab_provider_list=$(aws iam list-open-id-connect-providers --output json)
if ! jq -e --arg arn "$lab_provider_arn" '.OpenIDConnectProviderList | any(.Arn == $arn)' <<<"$lab_provider_list" >/dev/null; then
  aws iam create-open-id-connect-provider \
    --url https://token.actions.githubusercontent.com \
    --client-id-list sts.amazonaws.com
fi
# create-role fails if the role already exists; do not overwrite unrelated access.
aws iam create-role --role-name mongodb-lab-github-wake \
  --description 'GitHub lab default branch can start only the existing EC2 delegate' \
  --max-session-duration 3600 \
  --assume-role-policy-document "file://$lab_policy_dir/github-wake-trust.json"
aws iam put-role-policy --role-name mongodb-lab-github-wake \
  --policy-name StartExistingLabDelegate \
  --policy-document "file://$lab_policy_dir/github-wake-policy.json"
aws iam get-role --role-name mongodb-lab-github-wake \
  --query 'Role.{Arn:Arn,Trust:AssumeRolePolicyDocument}'
aws iam get-role-policy --role-name mongodb-lab-github-wake \
  --policy-name StartExistingLabDelegate
