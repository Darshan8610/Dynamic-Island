#!/usr/bin/env bash
# tracker.sh — Abstract issue tracker interface
#
# Reads docs/agents/issue-tracker.md to determine which backend to use,
# then delegates to the correct adapter (tracker-github.sh, tracker-gitlab.sh, etc.)
#
# Usage:
#   source skills/android-dev-workflow/scripts/lib/tracker.sh
#
#   tracker_create_issue "Title" "Body markdown" "label1,label2"
#   tracker_list_ready                           # List ready-for-agent issues
#   tracker_view_issue 42                        # Get issue title + body
#   tracker_comment_issue 42 "Progress update"
#   tracker_close_issue 42
#   tracker_add_label 42 "label"
#   tracker_remove_label 42 "label"
#   tracker_create_labels                        # Create triage labels

set -euo pipefail

LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
TRACKER_CONFIG="$REPO_ROOT/docs/agents/issue-tracker.md"

# --- Detect tracker backend ---

_detect_tracker() {
    if [ -f "$TRACKER_CONFIG" ]; then
        # Read the tracker type from the config header
        local tracker_line
        tracker_line=$(grep -i '^\s*tracker:' "$TRACKER_CONFIG" | head -1 | sed 's/.*tracker:\s*//' | tr '[:upper:]' '[:lower:]' | xargs)

        if [ -n "$tracker_line" ]; then
            echo "$tracker_line"
            return
        fi

        # Fallback: detect from config content
        if grep -qi 'gh issue\|gh cli\|github' "$TRACKER_CONFIG" 2>/dev/null; then
            echo "github"
        elif grep -qi 'glab\|gitlab' "$TRACKER_CONFIG" 2>/dev/null; then
            echo "gitlab"
        elif grep -qi '\.scratch\|local' "$TRACKER_CONFIG" 2>/dev/null; then
            echo "local"
        else
            echo "github"
        fi
    else
        # No config — auto-detect from git remote
        local remote
        remote=$(git remote get-url origin 2>/dev/null || echo "")
        if echo "$remote" | grep -qi 'gitlab'; then
            echo "gitlab"
        elif echo "$remote" | grep -qi 'github\|git@github'; then
            echo "github"
        else
            echo "github"
        fi
    fi
}

TRACKER_BACKEND="$(_detect_tracker)"

# --- Load the adapter ---

_load_adapter() {
    local adapter="$LIB_DIR/tracker-${TRACKER_BACKEND}.sh"
    if [ ! -f "$adapter" ]; then
        echo "Error: No adapter found for tracker '$TRACKER_BACKEND'" >&2
        echo "Expected: $adapter" >&2
        echo "Available adapters:" >&2
        ls "$LIB_DIR"/tracker-*.sh 2>/dev/null | sed 's/.*tracker-/  /' | sed 's/\.sh//' >&2
        exit 1
    fi
    source "$adapter"
}

_load_adapter

# --- Read triage label mappings ---

TRIAGE_CONFIG="$REPO_ROOT/docs/agents/triage-labels.md"

_read_label() {
    local role="$1"
    local default="$2"
    if [ -f "$TRIAGE_CONFIG" ]; then
        local mapped
        mapped=$(grep -i "| *$role *|" "$TRIAGE_CONFIG" | head -1 | awk -F'|' '{print $3}' | xargs)
        if [ -n "$mapped" ]; then
            echo "$mapped"
            return
        fi
    fi
    echo "$default"
}

# Canonical label mappings (overridden by triage-labels.md if present)
LABEL_READY_FOR_AGENT="$(_read_label "ready-for-agent" "ready-for-agent")"
LABEL_READY_FOR_HUMAN="$(_read_label "ready-for-human" "ready-for-human")"
LABEL_NEEDS_TRIAGE="$(_read_label "needs-triage" "needs-triage")"
LABEL_NEEDS_INFO="$(_read_label "needs-info" "needs-info")"
LABEL_WONTFIX="$(_read_label "wontfix" "wontfix")"
LABEL_PRD="$(_read_label "prd" "prd")"
LABEL_AFK="$(_read_label "afk" "afk")"
LABEL_HITL="$(_read_label "hitl" "hitl")"

# --- Public interface ---
# These functions are implemented by the loaded adapter.
# Each adapter MUST implement:
#
#   tracker_create_issue  TITLE BODY LABELS    → prints issue ID
#   tracker_list_ready                         → prints JSON: [{number, title, body}]
#   tracker_view_issue    ID                   → prints "TITLE\n\nBODY"
#   tracker_view_comments ID                   → prints comments
#   tracker_comment_issue ID BODY              → posts comment
#   tracker_close_issue   ID                   → closes issue
#   tracker_add_label     ID LABEL             → adds label
#   tracker_remove_label  ID LABEL             → removes label
#   tracker_create_labels                      → creates all triage labels
#   tracker_issue_state   ID                   → prints OPEN or CLOSED

# --- Convenience functions (built on the interface) ---

tracker_next_ready() {
    # Returns the first ready-for-agent issue that isn't blocked
    local issues
    issues=$(tracker_list_ready)

    if [ -z "$issues" ] || [ "$issues" = "[]" ] || [ "$issues" = "null" ]; then
        echo ""
        return
    fi

    echo "$issues" | jq -r '.[0] // empty'
}

tracker_is_blocked() {
    local id="$1"
    local body
    body=$(tracker_view_issue "$id" | tail -n +3)  # skip title + blank line

    # Extract blocked-by references
    local blocked_ids
    blocked_ids=$(echo "$body" | grep -i 'blocked by' -A5 | grep -oE '#[0-9]+' | sed 's/#//' || true)

    if [ -z "$blocked_ids" ]; then
        echo "false"
        return
    fi

    # Check if any blocker is still open
    while IFS= read -r blocker_id; do
        [ -z "$blocker_id" ] && continue
        local state
        state=$(tracker_issue_state "$blocker_id" 2>/dev/null || echo "UNKNOWN")
        if [ "$state" = "OPEN" ]; then
            echo "true:$blocker_id"
            return
        fi
    done <<< "$blocked_ids"

    echo "false"
}
