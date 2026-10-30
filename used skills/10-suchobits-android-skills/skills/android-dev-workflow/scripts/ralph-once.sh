#!/usr/bin/env bash
# Ralph Once — Single HITL (Human-In-The-Loop) iteration for Android
#
# Runs one agent iteration: read PRD, pick task, implement, test, commit.
# Human watches the output and can intervene.
#
# Usage:
#   bash skills/android-dev-workflow/scripts/ralph-once.sh
#   bash skills/android-dev-workflow/scripts/ralph-once.sh --model claude-opus-4-6
#
# Prerequisites:
#   - PRD.md in repo root (feature specification)
#   - progress.txt in repo root (empty on first run)
#   - Android SDK and Gradle configured
#   - claude CLI installed

set -euo pipefail

MODEL="${1:---model claude-sonnet-4-6}"
SKILLS_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(git rev-parse --show-toplevel)"

# Validate prerequisites
for file in PRD.md progress.txt; do
    if [ ! -f "$REPO_ROOT/$file" ]; then
        echo "Error: $file not found in repo root. Create it first." >&2
        echo "  cp $SKILLS_DIR/assets/PRD-TEMPLATE.md $REPO_ROOT/PRD.md" >&2
        echo "  touch $REPO_ROOT/progress.txt" >&2
        exit 1
    fi
done

echo "=== Ralph (HITL) — Android ==="
echo "Model: $MODEL"
echo "PRD: $REPO_ROOT/PRD.md"
echo "Progress: $REPO_ROOT/progress.txt"
echo

claude --permission-mode acceptEdits $MODEL -p "@PRD.md @progress.txt

You are an Android developer working through a feature plan. Follow these instructions exactly:

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

If all PRD tasks are complete, output <promise>COMPLETE</promise>.
"
