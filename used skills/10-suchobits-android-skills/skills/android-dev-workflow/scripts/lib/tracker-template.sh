#!/usr/bin/env bash
# tracker-template.sh — Template for adding a new issue tracker adapter
#
# To add support for a new tracker (e.g., Linear, Jira):
#
# 1. Copy this file:
#    cp tracker-template.sh tracker-{name}.sh
#
# 2. Implement every function below using your tracker's CLI or API.
#    Each function signature and contract is documented.
#
# 3. Add "{name}" as a detection case in tracker.sh (_detect_tracker function).
#
# 4. Create a docs/agents/issue-tracker.md config with "tracker: {name}".
#
# 5. Test:
#    source lib/tracker.sh
#    tracker_create_labels
#    tracker_create_issue "Test" "Body" "ready-for-agent"
#    tracker_list_ready
#    tracker_view_issue <id>
#    tracker_close_issue <id>
#
# The LABEL_* variables (LABEL_READY_FOR_AGENT, etc.) are set by tracker.sh
# from docs/agents/triage-labels.md. Use them instead of hardcoding label names.

# --- Required functions ---

tracker_create_issue() {
    # Create a new issue/ticket.
    # Args: TITLE (string), BODY (markdown string), LABELS (comma-separated string)
    # Output: Print the issue ID/number to stdout (just the number, nothing else)
    local title="$1" body="$2" labels="${3:-}"
    echo "NOT IMPLEMENTED: tracker_create_issue" >&2
    return 1
}

tracker_list_ready() {
    # List all open issues with the ready-for-agent label.
    # Output: Print JSON array to stdout: [{number: int, title: string, body: string}]
    #         Sorted by number ascending (oldest first).
    #         Empty array "[]" if none found.
    echo "NOT IMPLEMENTED: tracker_list_ready" >&2
    echo "[]"
}

tracker_view_issue() {
    # View a single issue.
    # Args: ID (string/number)
    # Output: Print "TITLE\n\nBODY" to stdout (title on first line, blank line, then body)
    local id="$1"
    echo "NOT IMPLEMENTED: tracker_view_issue" >&2
    return 1
}

tracker_view_comments() {
    # View comments/notes on an issue.
    # Args: ID (string/number)
    # Output: Print comments to stdout (freeform text, one per section)
    local id="$1"
    echo "NOT IMPLEMENTED: tracker_view_comments" >&2
}

tracker_comment_issue() {
    # Post a comment/note on an issue.
    # Args: ID (string/number), BODY (markdown string)
    # Output: None required (silent on success)
    local id="$1" body="$2"
    echo "NOT IMPLEMENTED: tracker_comment_issue" >&2
    return 1
}

tracker_close_issue() {
    # Close/complete an issue.
    # Args: ID (string/number)
    # Output: None required (silent on success)
    local id="$1"
    echo "NOT IMPLEMENTED: tracker_close_issue" >&2
    return 1
}

tracker_add_label() {
    # Add a label/tag to an issue.
    # Args: ID (string/number), LABEL (string)
    # Output: None required (silent on success)
    local id="$1" label="$2"
    echo "NOT IMPLEMENTED: tracker_add_label" >&2
    return 1
}

tracker_remove_label() {
    # Remove a label/tag from an issue.
    # Args: ID (string/number), LABEL (string)
    # Output: None required (silent on success)
    local id="$1" label="$2"
    echo "NOT IMPLEMENTED: tracker_remove_label" >&2
    return 1
}

tracker_issue_state() {
    # Get the state of an issue.
    # Args: ID (string/number)
    # Output: Print "OPEN" or "CLOSED" to stdout (uppercase)
    local id="$1"
    echo "NOT IMPLEMENTED: tracker_issue_state" >&2
    echo "UNKNOWN"
}

tracker_create_labels() {
    # Create all triage labels in the tracker.
    # Uses LABEL_* variables from tracker.sh.
    # Output: Print status per label (e.g., "  ✓ ready-for-agent (exists)")
    # Idempotent — safe to run multiple times.
    echo "NOT IMPLEMENTED: tracker_create_labels" >&2
    return 1
}
