# Issue-Based Task Dispatch

## Why Issues Over Files

`PRD.md` and `progress.txt` are fragile:
- An agent can overwrite or corrupt them
- A `git reset --hard` wipes progress
- Only visible on one machine
- No state machine (open/closed/blocked)
- Can't dispatch to multiple agents in parallel

GitHub Issues solve all of these. Each task is an issue with a label, acceptance criteria, blocking relationships, and a comment thread for agent progress.

## The Issue Pipeline

```
/grill-me ──→ /to-prd ──→ /to-issues ──→ Agent picks up ──→ PR
                │               │              │
           Publish PRD    Publish tasks    ready-for-agent
           as Issue #1    as Issues #2-N   label triggers
                                           Ralph loop
```

## Triage Labels

Set up these labels in your repo:

```bash
gh label create "needs-triage"     --color "FBCA04" --description "Maintainer needs to evaluate"
gh label create "needs-info"       --color "D93F0B" --description "Waiting on reporter input"
gh label create "ready-for-agent"  --color "0E8A16" --description "Fully specified, agent can implement AFK"
gh label create "ready-for-human"  --color "1D76DB" --description "Requires human implementation"
gh label create "prd"              --color "5319E7" --description "Product requirements document"
gh label create "hitl"             --color "E4E669" --description "Human-in-the-loop task"
gh label create "afk"              --color "0E8A16" --description "AFK-safe task"
```

## Phase 4: Publishing a PRD as an Issue

After drafting your PRD (from the template or a `/grill-me` session), publish it:

```bash
gh issue create \
    --title "PRD: {Feature Name}" \
    --label "prd,ready-for-agent" \
    --body "$(cat PRD.md)"
```

Or let the agent do it during a `/to-prd` invocation.

## Phase 5: Breaking PRD into Task Issues

Each task becomes its own issue with this structure:

```markdown
## Parent
Implements #1 (PRD: {Feature Name})

## What to Build
{Behavioral description of this vertical slice. Describe WHAT the system should do,
not HOW to implement it. No file paths — they go stale. The agent explores the
codebase fresh each time.}

## Acceptance Criteria
- [ ] {Observable behavior 1}
- [ ] {Observable behavior 2}
- [ ] Architecture guard passes
- [ ] Unit tests for new ViewModels
- [ ] All feedback loops green: `./gradlew architectureGuard spotlessCheck testDemoDebug`

## Blocked By
- #3 (data layer must exist before ViewModel can use it)
Or: None — can start immediately.

## Type
AFK — fully specified, no human decisions needed.
```

### Publishing in Dependency Order

Issues must be published **blockers first** so real issue numbers can be referenced:

```bash
# Slice 1: No blockers (publishes as, say, #2)
gh issue create --title "Create {Item} data layer end-to-end" \
    --label "ready-for-agent,afk" \
    --body "## Parent\nImplements #1\n\n## What to Build\n..."

# Slice 2: Blocked by #2
gh issue create --title "Scaffold {Feature} screen (tracer bullet)" \
    --label "ready-for-agent,afk" \
    --body "## Parent\nImplements #1\n\n## Blocked By\n- #2\n\n## What to Build\n..."

# Slice 3: Blocked by #2
gh issue create --title "Add ViewModel tests + error states" \
    --label "ready-for-agent,afk" \
    --body "## Parent\nImplements #1\n\n## Blocked By\n- #2\n\n## What to Build\n..."
```

### HITL vs AFK Typing

Mark each issue:

| Type | Label | When | Agent Behavior |
|------|-------|------|---------------|
| **AFK** | `afk` | Fully specified, no decisions needed | Agent implements, tests, PRs without human |
| **HITL** | `hitl` | Needs architectural decision, design review, or taste | Agent drafts, human reviews before merge |

Default to AFK. Use HITL for:
- First module creation (architectural decisions)
- UI-heavy screens (design taste)
- Integration with unfamiliar APIs
- Security-sensitive code

## Phase 6: Ralph Picks Up Issues

Instead of reading `PRD.md`, the Ralph loop queries GitHub Issues:

```bash
# Get the next ready-for-agent issue (oldest first, respects blocking)
NEXT_ISSUE=$(gh issue list \
    --label "ready-for-agent,afk" \
    --state open \
    --json number,title,body \
    --jq '.[0]')
```

The updated prompt references the issue:

```
You are an Android developer. Your task is described in GitHub Issue #{number}.

1. Read the issue: gh issue view {number}
2. Check blocked-by issues are closed. If not, skip and report.
3. Implement the acceptance criteria using tracer bullet discipline.
4. Run feedback loops: ./gradlew architectureGuard spotlessCheck testDemoDebug
5. Commit with message: "feat: {title} (closes #{number})"
6. Post a progress comment on the issue.
7. If all acceptance criteria pass, close the issue.
```

## Agent Progress Comments

After each iteration, the agent posts a comment on the issue:

```bash
gh issue comment {number} --body "## Progress Update
**Status:** In progress / Done
**Files changed:** core/model/.../Item.kt, feature/home/impl/.../HomeViewModel.kt
**Tests:** HomeViewModelTest — 4 passing
**Decisions:** Used combine() for two data sources
**Remaining:** Error states not yet implemented"
```

This replaces `progress.txt` — progress is attached to the issue, not a local file.

## Closing the Loop

When an agent finishes an issue:

```bash
# Commit references the issue
git commit -m "feat: create Item data layer end-to-end

Closes #2"

# GitHub auto-closes #2 when the PR merges
# Downstream issues (#3, #4) that were blocked by #2 become unblocked
```

## Parallel Dispatch

With issues, multiple agents can work simultaneously on non-blocking tasks:

```bash
# Agent 1 picks up issue #5 (UI polish — no blockers)
# Agent 2 picks up issue #6 (screenshot tests — no blockers)
# Both run in separate worktrees

git worktree add .worktrees/agent-1 -b agent/issue-5
git worktree add .worktrees/agent-2 -b agent/issue-6

# Each agent runs Ralph against their assigned issue
cd .worktrees/agent-1 && bash ../../skills/android-dev-workflow/scripts/ralph-issue.sh 5
cd .worktrees/agent-2 && bash ../../skills/android-dev-workflow/scripts/ralph-issue.sh 6
```

## Stale Issue Guard

Agent Briefs describe behavior, not file paths. But issues can still go stale if the codebase changes significantly between writing and implementation. Mitigate by:

1. **No file paths in issue bodies** — Describe interfaces and behavior. The agent explores fresh.
2. **No code snippets** (except prototype snippets encoding design decisions)
3. **Keep issues small** — Smaller scope = less time to go stale
4. **Close stale issues** — If an issue sits `ready-for-agent` for >2 weeks, re-triage it

## Migration: Files → Issues

If you started with `PRD.md` + `progress.txt` and want to switch:

```bash
# 1. Publish PRD as issue
gh issue create --title "PRD: $(head -1 PRD.md | sed 's/# PRD: //')" \
    --label "prd" --body "$(cat PRD.md)"

# 2. For each uncompleted task in PRD, create an issue
# (manually or via agent with /to-issues pattern)

# 3. Archive local files
mkdir -p .archive && mv PRD.md progress.txt .archive/

# 4. Update Ralph to use issue dispatch
# Use scripts/ralph-issue.sh instead of ralph-once.sh
```
