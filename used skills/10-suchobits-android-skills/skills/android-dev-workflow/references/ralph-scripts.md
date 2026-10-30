# Ralph Scripts — Android Implementation Loop

## What Is Ralph?

Ralph is a bash loop that repeatedly invokes an AI coding CLI against a plan file. The agent — not the human — chooses which task to work on. Each iteration: read plan → pick task → implement → test → commit → update progress. The loop terminates when the agent outputs `<promise>COMPLETE</promise>`.

Named after Ralph Wiggum from The Simpsons. Originated at [aihero.dev](https://www.aihero.dev).

## Two Modes

### HITL (Human-In-The-Loop)

**Script:** `scripts/ralph-once.sh`

One iteration. Human watches output, learns what the agent does well and poorly. Use for:
- First 2-3 iterations of any new feature
- Architectural tasks (module creation, navigation wiring)
- Tasks involving unfamiliar APIs
- Prompt refinement — adjust the PRD based on what you see

```bash
bash skills/android-dev-workflow/scripts/ralph-once.sh
```

### AFK (Away-From-Keyboard)

**Script:** `scripts/afk-ralph.sh`

Multiple iterations, unattended. Use after HITL proves the prompt produces clean commits. Always use with:
- Iteration cap (start with 5-10)
- Feedback loops that block bad commits
- Sandbox mode for safety (optional but recommended)

```bash
# 10 iterations, no sandbox
bash skills/android-dev-workflow/scripts/afk-ralph.sh 10

# 10 iterations in OrbStack/Docker sandbox
bash skills/android-dev-workflow/scripts/afk-ralph.sh 10 --sandbox
```

## The Ralph Cycle

Each iteration follows this sequence:

```
┌─────────────────────────────────────────────┐
│  1. Read PRD.md + progress.txt              │
│  2. Select highest-priority incomplete task  │
│  3. Implement (tracer bullet discipline)     │
│  4. Run feedback loops:                      │
│     ./gradlew architectureGuard              │
│     ./gradlew spotlessCheck                  │
│     ./gradlew testDemoDebug                  │
│  5. Fix failures, re-run until green         │
│  6. Commit                                   │
│  7. Update progress.txt                      │
│  8. All tasks done? → <promise>COMPLETE      │
│     │                  </promise>             │
│     └─ More tasks? → Next iteration ─────────┘
```

## Android Feedback Loops

These are the quality gates. The agent must pass all before committing:

| Check | Command | Time | What It Catches |
|-------|---------|------|-----------------|
| Architecture | `./gradlew architectureGuard` | ~5s | Module placement, dependency direction |
| Formatting | `./gradlew spotlessCheck` | ~20s | Code style, license headers |
| Unit tests | `./gradlew testDemoDebug` | 2-5min | Logic errors, regressions |
| Lint | `./gradlew lintProdRelease` | 2-3min | Source-level violations |

For faster iterations, use a subset during HITL:

```bash
# Fast feedback (< 30s)
./gradlew architectureGuard spotlessCheck

# Full feedback (before committing)
./gradlew architectureGuard spotlessCheck testDemoDebug
```

## Progress File Format

`progress.txt` is the agent's memory across context windows. Format:

```
## Iteration 1 — 2026-05-10T10:00
Task: Create {Item} data model + entity + mapping
Status: DONE
Files: core/model/src/.../Item.kt, core/database/src/.../ItemEntity.kt
Tests: None yet (model classes only)
Decisions: Used kotlinx.datetime.Instant for timestamps (consistent with project)
Next: Create ItemDao + repository interface

## Iteration 2 — 2026-05-10T10:45
Task: Create ItemDao + OfflineFirstItemRepository
Status: DONE
Files: core/database/src/.../ItemDao.kt, core/data/src/.../OfflineFirstItemRepository.kt
Tests: ItemDaoTest — 3 tests, OfflineFirstItemRepositoryTest — 2 tests
Decisions: Skipped use case layer (single data source, no combination needed)
Next: Scaffold feature module (NavKey + ViewModel + Screen)
```

**Delete progress.txt after the feature ships.** Stale progress misleads future agents.

## Task Priority Order

Force the agent to work on the right things first:

```
1. Architectural/integration work    (module structure, wiring)
2. Core data layer                   (models, DAOs, repositories)
3. Feature scaffolding               (NavKey, ViewModel, Screen)
4. Happy path implementation         (success state end-to-end)
5. Error/loading/empty states
6. Tests (ViewModel, screenshot)
7. Polish and edge cases
```

Agents naturally gravitate toward easy tasks (#7). The prompt explicitly tells them to prioritize by risk.

## Iteration Caps

| Task Size | HITL Iterations | AFK Iterations |
|-----------|----------------|----------------|
| Small (1 screen, no data layer) | 2-3 | 3-5 |
| Medium (1 feature, new module) | 3-5 | 8-12 |
| Large (multi-screen, new sync) | 5-8 | 15-25 |

Each iteration runs 30-45 minutes. Budget accordingly.

## Streaming AFK Output

To monitor AFK runs in real-time:

```bash
# Stream text output with jq
claude --output-format stream-json -p "$PROMPT" 2>&1 | \
    grep --line-buffered '^{' | \
    jq --unbuffered -rj 'select(.type == "assistant").message.content[]? | select(.type == "text").text // empty'
```

## Resuming After Failure

If an iteration fails (crash, timeout, API error):
1. Check `progress.txt` — it shows what was last completed
2. Check `git log` — the last commit shows the agent's last successful work
3. Run `ralph-once.sh` to do one supervised iteration from current state
4. If state is clean, resume with `afk-ralph.sh`

## Alternative Loop Types

Beyond the standard PRD loop, Ralph can run specialized loops:

### Test Coverage Loop

```bash
PROMPT="Analyze test coverage. Find the module with the lowest coverage.
Write tests for its most critical untested paths. Run ./gradlew testDemoDebug.
Commit when tests pass. Update progress.txt."
```

### Architecture Cleanup Loop

```bash
PROMPT="Run ./gradlew architectureGuard. If it passes, look for architectural
improvements: unused dependencies, modules that should be split, misplaced code.
Make one improvement per iteration. Run all checks before committing."
```

### Lint Fix Loop

```bash
PROMPT="Run ./gradlew lintProdRelease. Pick the highest-severity lint warning.
Fix it. Run lint again to verify. Commit the fix. Update progress.txt."
```
