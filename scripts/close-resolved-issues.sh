#!/usr/bin/env bash
#
# Closes the code review findings that sprints 0 through 4 actually resolved.
#
# Why this script exists: GitHub only auto-closes an issue when the pull request
# that references it merges into the *default* branch. Every sprint PR targets
# `dev`, so none of the "Closes #NNN" references ever fired, and promoting `dev`
# to `main` will not fire them retroactively either. On top of that, the
# integration token that filed these issues can create them but cannot close
# them, so this has to run as a real user.
#
# Run from a machine authenticated as a user with push access:
#
#   ./scripts/close-resolved-issues.sh
#
# On Windows use close-resolved-issues.ps1 instead, or run this one under Git
# Bash. Both read the same scripts/resolved-issues.tsv, so they cannot disagree
# about what has been fixed.
#
# It is idempotent: closing an already closed issue is a no-op.
#
# Every number in the data file was verified against the merged tree on `dev`,
# not against what a pull request description claimed. The deliberate omissions
# are listed at the bottom; read them before adding to the list.
#
set -euo pipefail

REPO="${REPO:-rons-space/Kaup}"
DATA="$(dirname "$0")/resolved-issues.tsv"

command -v gh >/dev/null || { echo "gh is required" >&2; exit 1; }
[ -f "$DATA" ] || { echo "missing $DATA" >&2; exit 1; }

section=""
closed=0
failed=0

# The IFS and -r combination stops read from eating backslashes or trimming
# whitespace inside a reason.
while IFS=$'\t' read -r line_section issue reason; do
  case "$line_section" in
    '#'*|'') continue ;;
  esac

  if [ "$line_section" != "$section" ]; then
    section="$line_section"
    echo "$section"
  fi

  if gh issue close "$issue" --repo "$REPO" --comment "$reason" >/dev/null 2>&1; then
    echo "  closed #$issue"
    closed=$((closed + 1))
  else
    echo "  #$issue FAILED (already closed, or insufficient permission)" >&2
    failed=$((failed + 1))
  fi
done < "$DATA"

echo
echo "$closed closed, $failed failed or already closed"

echo
echo "Deliberately NOT closed:"
echo "  #159  the encryption claims were withdrawn, but the database is still"
echo "        plaintext. There is no SQLCipher anywhere. Real work remains."
echo "  #178  the vulnerable ktor pin is gone and the build is proven green,"
echo "        but the toolchain skew half of the finding is unreviewed."
echo "  #203  ALPHA_DESTRUCTIVE_MIGRATION exists with a TODO, but nothing"
echo "        enforces its removal before v0.2-alpha. Schema 7 has now landed"
echo "        inside the destructive window, so this is still the binding"
echo "        constraint on the next schema change."
echo
echo "Still open and worth knowing:"
echo "  #269  moves LineItem.quantity to Quantity, the last Double in the money"
echo "        path. Not a schema change, so it is not gated on the migration"
echo "        window."
echo "  #174  :core-data and :feature-auth still have no test harness, so the"
echo "        transactional glue added in sprint 4, OverrideAuthorizer and"
echo "        HotpCodeIssuer, has no automated coverage. The pure policy under"
echo "        it does. This is the largest known gap in sprint 4."
