#!/usr/bin/env bash
# tracker-local.sh — Local markdown adapter
#
# Implements the tracker interface using .scratch/ directory.
# No external tools required. Issues are markdown files.
#
# Structure:
#   .scratch/{feature}/PRD.md
#   .scratch/{feature}/issues/01-data-layer.md
#   .scratch/{feature}/issues/02-feature-scaffold.md

SCRATCH_DIR="$REPO_ROOT/.scratch"

_next_issue_number() {
    local dir="$SCRATCH_DIR"
    local max=0
    for f in "$dir"/*/issues/*.md 2>/dev/null; do
        local num
        num=$(basename "$f" | grep -oE '^[0-9]+' | sed 's/^0*//')
        [ -n "$num" ] && [ "$num" -gt "$max" ] && max=$num
    done
    echo $((max + 1))
}

_slugify() {
    echo "$1" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/-/g' | sed 's/--*/-/g' | sed 's/^-//' | sed 's/-$//' | cut -c1-50
}

_find_issue_file() {
    local id="$1"
    local padded
    padded=$(printf '%02d' "$id")
    find "$SCRATCH_DIR" -path "*/issues/${padded}-*.md" -print -quit 2>/dev/null
}

_all_issue_files() {
    find "$SCRATCH_DIR" -path "*/issues/*.md" 2>/dev/null | sort
}

tracker_create_issue() {
    local title="$1" body="$2" labels="${3:-}"
    local num
    num=$(_next_issue_number)
    local padded
    padded=$(printf '%02d' "$num")
    local slug
    slug=$(_slugify "$title")

    # Find or create a feature directory
    local feature_dir
    feature_dir=$(find "$SCRATCH_DIR" -maxdepth 1 -type d ! -name '.scratch' | head -1)
    if [ -z "$feature_dir" ]; then
        feature_dir="$SCRATCH_DIR/current"
    fi
    mkdir -p "$feature_dir/issues"

    local filepath="$feature_dir/issues/${padded}-${slug}.md"
    cat > "$filepath" << EOF
# $title

Status: open
Labels: $labels
Created: $(date -Iseconds)

$body

## Comments
EOF
    echo "$num"
}

tracker_list_ready() {
    local result="["
    local first=true

    for f in $(_all_issue_files); do
        if grep -qi "Status: open" "$f" && grep -qi "$LABEL_READY_FOR_AGENT" "$f"; then
            local num
            num=$(basename "$f" | grep -oE '^[0-9]+' | sed 's/^0*//')
            local title
            title=$(head -1 "$f" | sed 's/^# //')
            local body
            body=$(sed '1,/^$/d' "$f" | sed '/^## Comments/,$d')

            [ "$first" = true ] || result+=","
            first=false
            result+=$(jq -n --arg n "$num" --arg t "$title" --arg b "$body" \
                '{number: ($n | tonumber), title: $t, body: $b}')
        fi
    done

    result+="]"
    echo "$result"
}

tracker_view_issue() {
    local id="$1"
    local filepath
    filepath=$(_find_issue_file "$id")
    if [ -z "$filepath" ]; then
        echo "Issue #$id not found" >&2
        return 1
    fi
    local title
    title=$(head -1 "$filepath" | sed 's/^# //')
    local body
    body=$(sed '1,/^$/d' "$filepath" | sed '/^## Comments/,$d')
    echo "$title"
    echo
    echo "$body"
}

tracker_view_comments() {
    local id="$1"
    local filepath
    filepath=$(_find_issue_file "$id")
    [ -z "$filepath" ] && return
    sed -n '/^## Comments/,$p' "$filepath" | tail -n +2
}

tracker_comment_issue() {
    local id="$1" body="$2"
    local filepath
    filepath=$(_find_issue_file "$id")
    [ -z "$filepath" ] && return 1
    echo "" >> "$filepath"
    echo "### $(date -Iseconds)" >> "$filepath"
    echo "$body" >> "$filepath"
}

tracker_close_issue() {
    local id="$1"
    local filepath
    filepath=$(_find_issue_file "$id")
    [ -z "$filepath" ] && return 1
    sed -i '' "s/^Status: open/Status: closed/" "$filepath" 2>/dev/null || \
    sed -i "s/^Status: open/Status: closed/" "$filepath"
}

tracker_add_label() {
    local id="$1" label="$2"
    local filepath
    filepath=$(_find_issue_file "$id")
    [ -z "$filepath" ] && return 1
    sed -i '' "s/^Labels: \(.*\)/Labels: \1,$label/" "$filepath" 2>/dev/null || \
    sed -i "s/^Labels: \(.*\)/Labels: \1,$label/" "$filepath"
}

tracker_remove_label() {
    local id="$1" label="$2"
    local filepath
    filepath=$(_find_issue_file "$id")
    [ -z "$filepath" ] && return 1
    sed -i '' "s/,$label//g; s/$label,//g; s/$label//g" "$filepath" 2>/dev/null || \
    sed -i "s/,$label//g; s/$label,//g; s/$label//g" "$filepath"
}

tracker_issue_state() {
    local id="$1"
    local filepath
    filepath=$(_find_issue_file "$id")
    [ -z "$filepath" ] && echo "UNKNOWN" && return
    if grep -qi "Status: closed" "$filepath"; then
        echo "CLOSED"
    else
        echo "OPEN"
    fi
}

tracker_create_labels() {
    echo "  ✓ Local tracker uses inline labels — no setup needed."
}
