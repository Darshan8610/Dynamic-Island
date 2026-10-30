#!/usr/bin/env bash
# Android Skills Installer
#
# Installs android agent skills into the current project directory,
# detecting which AI coding agents are available and placing skills
# in the correct locations for each.
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/suchobits/android-skills/main/install.sh | bash
#
# Options (via env vars):
#   AGENTS="claude,codex"    Install for specific agents only
#   SCOPE="user"             Install to user-level (~/) instead of project-level
#   BRANCH="main"            Git branch to install from
#   REPO="suchobits/android-skills"  GitHub repo

set -euo pipefail

REPO="${REPO:-suchobits/android-skills}"
BRANCH="${BRANCH:-main}"
SCOPE="${SCOPE:-project}"
AGENTS="${AGENTS:-auto}"
ARCHIVE_URL="https://github.com/$REPO/archive/refs/heads/$BRANCH.tar.gz"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

info()  { echo -e "${CYAN}▸${NC} $1"; }
ok()    { echo -e "${GREEN}✓${NC} $1"; }
warn()  { echo -e "${YELLOW}⚠${NC} $1"; }
err()   { echo -e "${RED}✗${NC} $1" >&2; }

echo -e "${BOLD}Android Skills Installer${NC}"
echo -e "Repo: ${CYAN}$REPO${NC} (branch: $BRANCH)"
echo

# --- Detect agents ---

detect_agents() {
    local found=()

    # Claude Code
    if command -v claude &>/dev/null || [ -d ".claude" ] || [ -d "$HOME/.claude" ]; then
        found+=("claude")
    fi

    # Gemini CLI
    if command -v gemini &>/dev/null || [ -d ".gemini" ] || [ -d "$HOME/.gemini" ]; then
        found+=("gemini")
    fi

    # OpenAI Codex
    if command -v codex &>/dev/null; then
        found+=("codex")
    fi

    # Cursor
    if [ -d ".cursor" ] || [ -d "$HOME/.cursor" ]; then
        found+=("cursor")
    fi

    # Junie
    if [ -d ".junie" ] || [ -d "$HOME/.junie" ]; then
        found+=("junie")
    fi

    # Windsurf
    if [ -d ".windsurf" ] || [ -d "$HOME/.codeium" ]; then
        found+=("windsurf")
    fi

    # Roo Code
    if [ -d ".roo" ] || [ -d "$HOME/.roo" ]; then
        found+=("roo")
    fi

    # OpenCode
    if command -v opencode &>/dev/null || [ -d ".opencode" ]; then
        found+=("opencode")
    fi

    # Goose
    if command -v goose &>/dev/null; then
        found+=("goose")
    fi

    # Always include cross-agent path if we found anything non-Claude
    # (Claude is the only agent that doesn't scan .agents/skills/)
    local has_non_claude=false
    for agent in "${found[@]}"; do
        if [ "$agent" != "claude" ]; then
            has_non_claude=true
            break
        fi
    done

    if [ "$has_non_claude" = true ]; then
        found+=("cross-agent")
    fi

    # If nothing detected, default to cross-agent + claude
    if [ ${#found[@]} -eq 0 ]; then
        warn "No agents detected. Installing to .agents/skills/ (cross-agent) and .claude/skills/"
        found=("cross-agent" "claude")
    fi

    echo "${found[@]}"
}

if [ "$AGENTS" = "auto" ]; then
    DETECTED=($(detect_agents))
    info "Detected agents: ${DETECTED[*]}"
else
    IFS=',' read -ra DETECTED <<< "$AGENTS"
    info "Installing for: ${DETECTED[*]}"
fi

echo

# --- Download skills ---

TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

info "Downloading skills from $REPO..."

if command -v curl &>/dev/null; then
    curl -fsSL "$ARCHIVE_URL" | tar xz -C "$TMPDIR" --strip-components=1
elif command -v wget &>/dev/null; then
    wget -qO- "$ARCHIVE_URL" | tar xz -C "$TMPDIR" --strip-components=1
else
    # Fallback to git clone
    git clone --depth 1 --branch "$BRANCH" "https://github.com/$REPO.git" "$TMPDIR/repo" 2>/dev/null
    mv "$TMPDIR/repo/"* "$TMPDIR/"
fi

if [ ! -d "$TMPDIR/skills" ]; then
    err "Download failed or skills/ directory not found in repo."
    exit 1
fi

SKILL_COUNT=$(find "$TMPDIR/skills" -name "SKILL.md" | wc -l | xargs)
ok "Downloaded $SKILL_COUNT skills"
echo

# --- Install to detected agent paths ---

install_skills() {
    local dest="$1"
    local label="$2"

    mkdir -p "$dest"
    cp -r "$TMPDIR/skills/"* "$dest/"
    ok "$label → $dest"
}

for agent in "${DETECTED[@]}"; do
    case "$agent" in
        claude)
            if [ "$SCOPE" = "user" ]; then
                install_skills "$HOME/.claude/skills" "Claude Code (user)"
            else
                install_skills ".claude/skills" "Claude Code (project)"
            fi
            ;;
        cross-agent)
            if [ "$SCOPE" = "user" ]; then
                install_skills "$HOME/.agents/skills" "Cross-agent (user)"
            else
                install_skills ".agents/skills" "Cross-agent (project)"
            fi
            ;;
        gemini)
            if [ "$SCOPE" = "user" ]; then
                install_skills "$HOME/.gemini/skills" "Gemini CLI (user)"
            else
                # .agents/skills/ covers gemini at project level
                true
            fi
            ;;
        junie)
            if [ "$SCOPE" = "user" ]; then
                install_skills "$HOME/.junie/skills" "Junie (user)"
            else
                install_skills ".junie/skills" "Junie (project)"
            fi
            ;;
        windsurf)
            if [ "$SCOPE" = "user" ]; then
                install_skills "$HOME/.codeium/windsurf/skills" "Windsurf (user)"
            else
                # .agents/skills/ covers windsurf at project level
                true
            fi
            ;;
        roo)
            if [ "$SCOPE" = "user" ]; then
                install_skills "$HOME/.roo/skills" "Roo Code (user)"
            else
                # .agents/skills/ covers roo at project level
                true
            fi
            ;;
        # codex, cursor, opencode, goose all use .agents/skills/ — covered by cross-agent
        codex|cursor|opencode|goose)
            true
            ;;
    esac
done

echo
echo -e "${BOLD}Installed skills:${NC}"
for skill in "$TMPDIR/skills"/*/SKILL.md; do
    name=$(basename $(dirname "$skill"))
    desc=$(grep 'description:' "$skill" | head -1 | sed 's/.*description: *>*-*//' | sed 's/^ *//' | cut -c1-80)
    echo -e "  ${GREEN}$name${NC}"
done

echo
echo -e "${BOLD}Verify:${NC} Ask your agent \"What Android skills do you have?\""
echo -e "${BOLD}Docs:${NC}   https://github.com/$REPO#readme"
