#!/usr/bin/env bash
# tracker-gitlab.sh — GitLab Issues adapter (glab CLI)
#
# Implements the tracker interface for GitLab Issues.
# Requires: glab CLI authenticated

tracker_create_issue() {
    local title="$1" body="$2" labels="${3:-}"
    local args=(--title "$title" --description "$body")
    [ -n "$labels" ] && args+=(--label "$labels")
    glab issue create "${args[@]}" --no-editor 2>/dev/null | grep -oE '#[0-9]+' | sed 's/#//'
}

tracker_list_ready() {
    # glab doesn't output JSON by default — build it manually
    local issues
    issues=$(glab issue list --label "$LABEL_READY_FOR_AGENT" --output-format json 2>/dev/null || echo "[]")
    echo "$issues" | jq '[.[] | {number: .iid, title: .title, body: .description}] | sort_by(.number)' 2>/dev/null || echo "[]"
}

tracker_view_issue() {
    local id="$1"
    local json
    json=$(glab issue view "$id" --output json 2>/dev/null)
    local title body
    title=$(echo "$json" | jq -r '.title // empty')
    body=$(echo "$json" | jq -r '.description // empty')
    echo "$title"
    echo
    echo "$body"
}

tracker_view_comments() {
    local id="$1"
    glab issue note list "$id" 2>/dev/null || true
}

tracker_comment_issue() {
    local id="$1" body="$2"
    glab issue note "$id" --message "$body" 2>/dev/null
}

tracker_close_issue() {
    local id="$1"
    glab issue close "$id" 2>/dev/null
}

tracker_add_label() {
    local id="$1" label="$2"
    glab issue update "$id" --label "$label" 2>/dev/null
}

tracker_remove_label() {
    local id="$1" label="$2"
    glab issue update "$id" --unlabel "$label" 2>/dev/null
}

tracker_issue_state() {
    local id="$1"
    local state
    state=$(glab issue view "$id" --output json 2>/dev/null | jq -r '.state // "unknown"')
    case "$state" in
        opened) echo "OPEN" ;;
        closed) echo "CLOSED" ;;
        *)      echo "UNKNOWN" ;;
    esac
}

tracker_create_labels() {
    local -A labels=(
        ["$LABEL_NEEDS_TRIAGE"]="Maintainer needs to evaluate"
        ["$LABEL_NEEDS_INFO"]="Waiting on reporter input"
        ["$LABEL_READY_FOR_AGENT"]="Fully specified, agent can implement AFK"
        ["$LABEL_READY_FOR_HUMAN"]="Requires human implementation"
        ["$LABEL_PRD"]="Product requirements document"
        ["$LABEL_HITL"]="Human-in-the-loop, needs review during implementation"
        ["$LABEL_AFK"]="AFK-safe, agent can complete without human"
        ["$LABEL_WONTFIX"]="Will not be actioned"
    )

    for name in "${!labels[@]}"; do
        local desc="${labels[$name]}"
        if glab label list 2>/dev/null | grep -q "$name"; then
            echo "  ✓ $name (exists)"
        else
            glab label create "$name" --description "$desc" 2>/dev/null
            echo "  + $name (created)"
        fi
    done
}
