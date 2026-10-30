#!/usr/bin/env bash
# AFK Ralph — Autonomous Android implementation loop
#
# Runs multiple agent iterations unattended. Each iteration:
# reads PRD, picks task, implements, runs Gradle checks, commits, updates progress.
# Stops on <promise>COMPLETE</promise> or iteration cap.
#
# Usage:
#   bash skills/android-dev-workflow/scripts/afk-ralph.sh 10          # 10 iterations max
#   bash skills/android-dev-workflow/scripts/afk-ralph.sh 5 --sandbox # Run in OrbStack/Docker
#
# Prerequisites:
#   - PRD.md and progress.txt in repo root
#   - Android SDK and Gradle configured
#   - claude CLI installed
#   - For --sandbox mode: OrbStack or Docker running

set -euo pipefail

MAX_ITERATIONS="${1:-10}"
USE_SANDBOX=false
if [[ "${2:-}" == "--sandbox" ]]; then
    USE_SANDBOX=true
fi

SKILLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel)"
LOG_DIR="$REPO_ROOT/.ralph-logs"
mkdir -p "$LOG_DIR"

# Validate prerequisites
for file in PRD.md progress.txt; do
    if [ ! -f "$REPO_ROOT/$file" ]; then
        echo "Error: $file not found in repo root." >&2
        exit 1
    fi
done

# Build the prompt (shared between sandbox and non-sandbox)
PROMPT="@PRD.md @progress.txt

You are an Android developer working through a feature plan autonomously. Follow these instructions exactly:

1. READ the PRD and progress file. Understand what has been done and what remains.

2. SELECT the highest-priority incomplete task. Prefer:
   - Architectural/integration tasks over leaf tasks
   - Tasks that unblock other tasks
   - Vertical slices (touching all layers) over horizontal work

3. IMPLEMENT the task using tracer bullet discipline:
   - Build the smallest end-to-end slice first
   - Touch all relevant layers: model → entity → DAO → repository → ViewModel → Screen → test
   - Do NOT build an entire layer before moving to the next

4. RUN FEEDBACK LOOPS before committing:
   ./gradlew architectureGuard    # Module structure check
   ./gradlew spotlessCheck        # Code formatting
   ./gradlew testDemoDebug        # Unit tests
   If any fail, fix the issue and re-run. Do not commit until all pass.

5. COMMIT your changes with a descriptive message.

6. UPDATE progress.txt with:
   - Iteration number and timestamp
   - Task completed
   - Files changed
   - Tests added/passing
   - Decisions made and why
   - What to do next

ONLY WORK ON A SINGLE TASK. Stop after one task is complete.

If all PRD tasks are complete, output <promise>COMPLETE</promise>."

echo "=== AFK Ralph — Android ==="
echo "Max iterations: $MAX_ITERATIONS"
echo "Sandbox: $USE_SANDBOX"
echo "Logs: $LOG_DIR/"
echo "Started: $(date -Iseconds)"
echo

for ((i=1; i<=MAX_ITERATIONS; i++)); do
    echo "--- Iteration $i / $MAX_ITERATIONS --- $(date -Iseconds)"
    LOG_FILE="$LOG_DIR/iteration-$(printf '%03d' $i)-$(date +%Y%m%d-%H%M%S).log"

    if [ "$USE_SANDBOX" = true ]; then
        # OrbStack/Docker sandbox mode
        # Bind-mount the repo, use host's Android SDK via ANDROID_HOME
        result=$(docker run --rm \
            -v "$REPO_ROOT:/workspace" \
            -v "${ANDROID_HOME:-$HOME/Library/Android/sdk}:/android-sdk:ro" \
            -v "${GRADLE_USER_HOME:-$HOME/.gradle}:/gradle-cache:ro" \
            -e ANDROID_HOME=/android-sdk \
            -e GRADLE_USER_HOME=/gradle-cache \
            -e ANTHROPIC_API_KEY="${ANTHROPIC_API_KEY}" \
            -w /workspace \
            android-ralph:latest \
            claude --dangerously-skip-permissions --output-format stream-json -p "$PROMPT" \
            2>&1 | tee "$LOG_FILE")
    else
        # Direct mode (no container)
        result=$(claude --permission-mode acceptEdits --output-format stream-json -p "$PROMPT" \
            2>&1 | tee "$LOG_FILE")
    fi

    # Check for completion signal
    if echo "$result" | grep -q '<promise>COMPLETE</promise>'; then
        echo
        echo "=== PRD COMPLETE after $i iteration(s) ==="
        echo "Finished: $(date -Iseconds)"
        echo "Logs: $LOG_DIR/"

        # Summary
        echo
        echo "=== Commits made ==="
        git log --oneline -n "$i" 2>/dev/null || true
        exit 0
    fi

    echo "Iteration $i complete. Continuing..."
    echo
done

echo
echo "=== Reached iteration cap ($MAX_ITERATIONS). PRD may not be complete. ==="
echo "Check progress.txt and PRD.md for remaining tasks."
echo "Run again with a higher cap: bash $0 $((MAX_ITERATIONS + 10))"
exit 0
