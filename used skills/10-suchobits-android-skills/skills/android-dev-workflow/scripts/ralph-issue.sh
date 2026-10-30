#!/usr/bin/env bash
# Ralph Issue — Work on a specific issue from any supported tracker
#
# Picks up an issue by number, implements it, posts progress, closes when done.
# Uses the tracker abstraction layer — works with GitHub, GitLab, or local markdown.
#
# Usage:
#   bash skills/android-dev-workflow/scripts/ralph-issue.sh 42          # Work on issue #42
#   bash skills/android-dev-workflow/scripts/ralph-issue.sh 42 3        # Max 3 iterations
#   bash skills/android-dev-workflow/scripts/ralph-issue.sh next        # Pick next ready-for-agent issue
#
# Prerequisites:
#   - Tracker configured (run android-setup skill, or create docs/agents/issue-tracker.md)
#   - Tracker CLI authenticated (gh, glab, etc.)
#   - Android SDK and Gradle configured

set -euo pipefail

SCRIPTS_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPTS_DIR/lib/tracker.sh"

ISSUE_ARG="${1:?Usage: ralph-issue.sh <issue-number|next> [max-iterations]}"
MAX_ITERATIONS="${2:-5}"

# Resolve issue number
if [ "$ISSUE_ARG" = "next" ]; then
    echo "Finding next ready-for-agent issue ($TRACKER_BACKEND)..."

    NEXT_JSON=$(tracker_next_ready)
    if [ -z "$NEXT_JSON" ]; then
        echo "No ready-for-agent issues found."
        exit 0
    fi

    ISSUE_NUM=$(echo "$NEXT_JSON" | jq -r '.number')
    echo "Selected issue #$ISSUE_NUM"
else
    ISSUE_NUM="$ISSUE_ARG"
fi

# Fetch issue details
ISSUE_VIEW=$(tracker_view_issue "$ISSUE_NUM")
ISSUE_TITLE=$(echo "$ISSUE_VIEW" | head -1)
ISSUE_BODY=$(echo "$ISSUE_VIEW" | tail -n +3)

echo "=== Ralph Issue — Android ($TRACKER_BACKEND) ==="
echo "Issue: #$ISSUE_NUM — $ISSUE_TITLE"
echo "Max iterations: $MAX_ITERATIONS"
echo

# Check if blocked
BLOCKED=$(tracker_is_blocked "$ISSUE_NUM")
if [[ "$BLOCKED" == true:* ]]; then
    BLOCKER_ID="${BLOCKED#true:}"
    echo "Blocked by #$BLOCKER_ID (still open). Cannot proceed."
    echo "Work on #$BLOCKER_ID first, or remove the blocking relationship."
    exit 1
fi

# Post "starting work" comment
tracker_comment_issue "$ISSUE_NUM" "**Agent starting work** — $(date -Iseconds)
Tracker: $TRACKER_BACKEND | Max iterations: $MAX_ITERATIONS"

# Get previous progress
PREV_PROGRESS=$(tracker_view_comments "$ISSUE_NUM" 2>/dev/null | tail -30 || true)

for ((i=1; i<=MAX_ITERATIONS; i++)); do
    echo "--- Iteration $i / $MAX_ITERATIONS --- $(date -Iseconds)"

    result=$(claude --permission-mode acceptEdits --output-format stream-json -p "
You are an Android developer implementing a task from Issue #$ISSUE_NUM.

## Issue
Title: $ISSUE_TITLE

$ISSUE_BODY

## Previous Progress
$PREV_PROGRESS

## Instructions

1. READ the issue description and any previous progress comments above.

2. CHECK if blocked-by issues are resolved. If any blocker is still open, report it and stop.

3. IMPLEMENT the next piece of work toward the acceptance criteria:
   - Use tracer bullet discipline: vertical slices through all layers
   - Touch model → entity → DAO → repository → ViewModel → Screen → test as needed
   - Do NOT build an entire layer before moving to the next

4. RUN FEEDBACK LOOPS before committing:
   ./gradlew architectureGuard    # Module structure
   ./gradlew spotlessCheck        # Formatting
   ./gradlew testDemoDebug        # Unit tests
   Fix any failures before proceeding.

5. COMMIT with message: feat: {description} (refs #$ISSUE_NUM)
   Use 'closes #$ISSUE_NUM' in the FINAL commit only (when all acceptance criteria are met).

6. Report your progress. Summarize:
   - Status: In progress or Done
   - Files changed
   - Tests added/results
   - Decisions made and why
   - What remains

7. If ALL acceptance criteria are met, output <promise>COMPLETE</promise>
   Otherwise, describe what remains and stop this iteration.

WORK ON ONE STEP AT A TIME. Stop after meaningful progress.
" 2>&1 | tee /dev/stderr | tail -1)

    # Post progress comment
    tracker_comment_issue "$ISSUE_NUM" "## Progress — Iteration $i
$(date -Iseconds)
Iteration $i of $MAX_ITERATIONS completed." 2>/dev/null || true

    # Check for completion
    if echo "$result" | grep -q '<promise>COMPLETE</promise>'; then
        echo
        echo "=== Issue #$ISSUE_NUM COMPLETE after $i iteration(s) ==="

        ISSUE_STATE=$(tracker_issue_state "$ISSUE_NUM")
        if [ "$ISSUE_STATE" = "OPEN" ]; then
            tracker_comment_issue "$ISSUE_NUM" "## Complete
All acceptance criteria met."
            tracker_close_issue "$ISSUE_NUM"
            echo "Issue #$ISSUE_NUM closed."
        fi

        tracker_remove_label "$ISSUE_NUM" "$LABEL_READY_FOR_AGENT" 2>/dev/null || true

        echo
        echo "Commits:"
        git log --oneline -n "$i" 2>/dev/null || true
        exit 0
    fi

    # Refresh progress for next iteration
    PREV_PROGRESS=$(tracker_view_comments "$ISSUE_NUM" 2>/dev/null | tail -30 || true)

    echo "Iteration $i complete. Continuing..."
done

echo
echo "=== Reached iteration cap ($MAX_ITERATIONS) for issue #$ISSUE_NUM ==="
tracker_comment_issue "$ISSUE_NUM" "**Agent reached iteration cap** ($MAX_ITERATIONS iterations).
Remaining work needs manual continuation or a higher cap."
