#!/usr/bin/env bash
# Ralph Dispatch — Continuously work through ready-for-agent issues
#
# Picks up the next unblocked ready-for-agent issue, works on it until done,
# then picks the next. Uses the tracker abstraction layer.
#
# Usage:
#   bash skills/android-dev-workflow/scripts/ralph-dispatch.sh              # Process all
#   bash skills/android-dev-workflow/scripts/ralph-dispatch.sh 3            # Max 3 issues
#   bash skills/android-dev-workflow/scripts/ralph-dispatch.sh 3 5          # 3 issues, 5 iterations each
#
# Prerequisites:
#   - Tracker configured (docs/agents/issue-tracker.md)
#   - Tracker CLI authenticated
#   - Android SDK and Gradle configured

set -euo pipefail

MAX_ISSUES="${1:-999}"
ITERATIONS_PER_ISSUE="${2:-5}"
SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"

source "$SCRIPTS_DIR/lib/tracker.sh"

ISSUES_COMPLETED=0

echo "=== Ralph Dispatch — Android ($TRACKER_BACKEND) ==="
echo "Max issues: $MAX_ISSUES"
echo "Iterations per issue: $ITERATIONS_PER_ISSUE"
echo "Started: $(date -Iseconds)"
echo

for ((issue_num=1; issue_num<=MAX_ISSUES; issue_num++)); do
    NEXT_JSON=$(tracker_next_ready)

    if [ -z "$NEXT_JSON" ]; then
        echo "No more ready-for-agent issues. Done."
        break
    fi

    ISSUE_ID=$(echo "$NEXT_JSON" | jq -r '.number')
    ISSUE_TITLE=$(echo "$NEXT_JSON" | jq -r '.title')

    # Check if blocked
    BLOCKED=$(tracker_is_blocked "$ISSUE_ID")
    if [[ "$BLOCKED" == true:* ]]; then
        BLOCKER="${BLOCKED#true:}"
        echo "Skipping #$ISSUE_ID — blocked by #$BLOCKER"
        tracker_add_label "$ISSUE_ID" "$LABEL_NEEDS_TRIAGE" 2>/dev/null || true
        continue
    fi

    echo "=== Issue $issue_num: #$ISSUE_ID — $ISSUE_TITLE ==="

    bash "$SCRIPTS_DIR/ralph-issue.sh" "$ISSUE_ID" "$ITERATIONS_PER_ISSUE" || {
        echo "Issue #$ISSUE_ID did not complete. Moving to next."
        tracker_add_label "$ISSUE_ID" "$LABEL_NEEDS_TRIAGE" 2>/dev/null || true
    }

    ISSUES_COMPLETED=$((ISSUES_COMPLETED + 1))
    echo
done

echo "=== Dispatch complete ==="
echo "Issues processed: $ISSUES_COMPLETED"
echo "Finished: $(date -Iseconds)"
