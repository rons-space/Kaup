#!/usr/bin/env bash
#
# One-time backfill of MoSCoW labels and milestones onto the issue tracker.
#
# Why this script exists: issues #1-#146 were generated from ROADMAP.md with no
# milestone and no priority label, so the MoSCoW ordering the roadmap is built
# around was invisible on the board. Issues #158-#260 (the code review findings
# and the roadmap gaps) were filed by an integration token that can create
# issues but cannot set labels or milestones, so their intended metadata was
# written into the issue body and mirrored in scripts/issue-metadata.tsv.
#
# Run once, from a machine authenticated as a user with push access:
#
#   ./scripts/apply-issue-metadata.sh
#
# It is idempotent: re-running it re-applies the same labels and milestones.
#
set -euo pipefail

REPO="${REPO:-rons-space/Kaup}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAP="$HERE/issue-metadata.tsv"

command -v gh >/dev/null || { echo "gh is required" >&2; exit 1; }
[ -f "$MAP" ] || { echo "missing $MAP" >&2; exit 1; }

apply() { # $1=issue  $2=comma-separated labels  $3=milestone
  if gh issue edit "$1" --repo "$REPO" --add-label "$2" --milestone "$3" >/dev/null; then
    echo "  #$1  $2  ($3)"
  else
    echo "  #$1  FAILED" >&2
  fi
}

# --- 1. The roadmap-generated issues, by MoSCoW section ---------------------
# Ranges follow ROADMAP.md order exactly: the generator walked the file top to
# bottom, so section boundaries are contiguous.
#   1-64    Must Have    (Foundation, auth, POS, inventory, settings, sync)
#   65-117  Should Have  (auth/POS/inventory additions, customers, suppliers,
#                         expenses, sales, reports, settings, notifications,
#                         IzzyOnDroid)
#   118-140 Could Have
#   141-146 Manual test checklist, a release gate for every tag
echo "Roadmap issues 1-64 -> must-have / v0.1-alpha"
for n in $(seq 1 64); do apply "$n" "must-have" "v0.1-alpha"; done

echo "Roadmap issues 65-117 -> should-have / v0.2-alpha"
for n in $(seq 65 117); do apply "$n" "should-have" "v0.2-alpha"; done

echo "Roadmap issues 118-140 -> could-have / v1.x"
for n in $(seq 118 140); do apply "$n" "could-have" "v1.x"; done

echo "Roadmap issues 141-146 -> must-have / v0.1-alpha (release gate)"
for n in $(seq 141 146); do apply "$n" "must-have" "v0.1-alpha"; done

# --- 2. Code review findings and roadmap gaps, from the mapping file --------
echo "Code review and gap issues, from $(basename "$MAP")"
while IFS=$'\t' read -r number labels milestone _title; do
  [ -n "${number:-}" ] || continue
  apply "$number" "$labels" "$milestone"
done < "$MAP"

# --- 3. Housekeeping --------------------------------------------------------
# #157 was an accidental token capability probe and carries no content.
gh issue close 157 --repo "$REPO" --reason "not planned" \
  --comment "Accidental probe issue, no content." >/dev/null 2>&1 \
  && echo "Closed #157 (probe)" || true

echo "Done."
