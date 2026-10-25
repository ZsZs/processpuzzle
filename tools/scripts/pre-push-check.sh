#!/usr/bin/env bash
# Pre-push gate: lint what this branch changed with the Sonar-aligned ESLint rules (eslint-plugin-sonarjs) and
# run Checkstyle on the touched Maven modules, so findings are fixed before the PR instead of after Sonar.
# Only files changed against the merge-base with develop are checked, so the existing baseline never blocks a push.
# Skip once with SKIP_PRE_PUSH=1 (or `git push --no-verify`).
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
[ "${SKIP_PRE_PUSH:-}" = "1" ] && { echo "pre-push: skipped"; exit 0; }

base_ref="origin/develop"
git rev-parse --verify -q "$base_ref" >/dev/null || { echo "pre-push: $base_ref not found, skipping"; exit 0; }
base="$(git merge-base HEAD "$base_ref")"
changed() { git diff --name-only --diff-filter=ACMR "$base" HEAD -- "$@"; }

status=0

mapfile -t js < <(changed '*.ts' '*.tsx' '*.js' '*.mjs' '*.cjs')
if [ ${#js[@]} -gt 0 ]; then
  echo "pre-push: ESLint (Sonar rules) on ${#js[@]} changed file(s)"
  npx eslint -c eslint.sonar.config.mjs --no-warn-ignored "${js[@]}" || status=1
fi

mapfile -t java < <(changed '*.java')
if [ ${#java[@]} -gt 0 ]; then
  modules=()
  for f in "${java[@]}"; do
    d="$(dirname "$f")"
    while [ "$d" != "." ] && [ ! -f "$d/pom.xml" ]; do d="$(dirname "$d")"; done
    [ "$d" != "." ] && modules+=("$d")
  done
  if [ ${#modules[@]} -gt 0 ]; then
    list="$(printf '%s\n' "${modules[@]}" | sort -u | paste -sd, -)"
    echo "pre-push: Checkstyle on $list"
    mvn -q -B -pl "$list" validate || status=1

    # PMD (cognitive complexity, Sonar java:S3776) analyses whole modules, so run the report goal and keep only
    # violations in files this branch changed -- the module's existing ones must not block the push.
    echo "pre-push: PMD on $list"
    if mvn -q -B -pl "$list" pmd:pmd; then
      printf '%s\n' "${java[@]}" > "$(git rev-parse --git-dir)/pre-push-java-changed.txt"
      node tools/scripts/pmd-changed-violations.mjs "$(git rev-parse --git-dir)/pre-push-java-changed.txt" "${modules[@]}" || status=1
    else
      status=1
    fi
  fi
fi

[ $status -eq 0 ] || echo "pre-push: fix the findings above (or SKIP_PRE_PUSH=1 to bypass once)"
exit $status
