#!/usr/bin/env bash
# tracker-github.sh — GitHub Issues adapter (gh CLI)
#
# Implements the tracker interface for GitHub Issues.
# Requires: gh CLI authenticated

tracker_create_issue() {
    local title="$1" body="$2" labels="${3:-}"
    local args=(--title "$title" --body "$body")
    [ -n "$labels" ] && args+=(--label "$labels")
    gh issue create "${args[@]}" 2>/dev/null | grep -oE '[0-9]+$'
}

tracker_list_ready() {
    gh issue list \
        --label "$LABEL_READY_FOR_AGENT" \
        --state open \
        --json number,title,body \
        --jq 'sort_by(.number)' 2>/dev/null || echo "[]"
}

tracker_view_issue() {
    local id="$1"
    local title body
    title=$(gh issue view "$id" --json title --jq '.title' 2>/dev/null)
    body=$(gh issue view "$id" --json body --jq '.body' 2>/dev/null)
    echo "$title"
    echo
    echo "$body"
}

tracker_view_comments() {
    local id="$1"
    gh issue view "$id" --json comments \
        --jq '.comments[] | "---\n\(.author.login) (\(.createdAt)):\n\(.body)\n"' 2>/dev/null || true
}

tracker_comment_issue() {
    local id="$1" body="$2"
    gh issue comment "$id" --body "$body" 2>/dev/null
}

tracker_close_issue() {
    local id="$1"
    gh issue close "$id" 2>/dev/null
}

tracker_add_label() {
    local id="$1" label="$2"
    gh issue edit "$id" --add-label "$label" 2>/dev/null
}

tracker_remove_label() {
    local id="$1" label="$2"
    gh issue edit "$id" --remove-label "$label" 2>/dev/null
}

tracker_issue_state() {
    local id="$1"
    gh issue view "$id" --json state --jq '.state' 2>/dev/null | tr '[:lower:]' '[:upper:]'
}

tracker_create_labels() {
    local -A labels=(
        ["$LABEL_NEEDS_TRIAGE"]="FBCA04|Maintainer needs to evaluate"
        ["$LABEL_NEEDS_INFO"]="D93F0B|Waiting on reporter input"
        ["$LABEL_READY_FOR_AGENT"]="0E8A16|Fully specified, agent can implement AFK"
        ["$LABEL_READY_FOR_HUMAN"]="1D76DB|Requires human implementation"
        ["$LABEL_PRD"]="5319E7|Product requirements document"
        ["$LABEL_HITL"]="E4E669|Human-in-the-loop, needs review during implementation"
        ["$LABEL_AFK"]="0E8A16|AFK-safe, agent can complete without human"
        ["$LABEL_WONTFIX"]="FFFFFF|Will not be actioned"
    )

    for name in "${!labels[@]}"; do
        local color="${labels[$name]%%|*}"
        local desc="${labels[$name]#*|}"
        if gh label list --json name --jq '.[].name' 2>/dev/null | grep -qx "$name"; then
            echo "  ✓ $name (exists)"
        else
            gh label create "$name" --color "$color" --description "$desc" 2>/dev/null
            echo "  + $name (created)"
        fi
    done
}
