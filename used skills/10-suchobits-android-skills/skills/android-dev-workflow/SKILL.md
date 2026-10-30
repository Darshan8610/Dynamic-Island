---
name: android-dev-workflow
description: >-
  Orchestrates the full Android development lifecycle from idea to ship using autonomous
  agent loops. Use when planning an Android feature end-to-end, running Ralph-style
  autonomous implementation loops, writing a PRD for Android features, breaking work into
  agent-executable tasks, setting up tracer bullet development, configuring AFK agent
  runs with Gradle feedback loops, or coordinating multi-phase Android development
  across ideation, planning, implementation, and QA.
license: Apache-2.0
compatibility: Requires Android SDK, Gradle, git. Optional OrbStack/Docker for AFK sandboxing.
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: google/nowinandroid, mattpocock/skills, aihero.dev/ralph
---

# Android Dev Workflow — Autonomous Implementation Pipeline

Orchestrates the full lifecycle of Android feature development using autonomous agent loops, domain-specific skills, and automated quality gates. This is the meta-skill that ties all other android-* skills together into an actionable pipeline.

## Philosophy

Three rules govern this workflow:

1. **The build is the enforcer.** Every quality check runs as part of `./gradlew build`. Humans and agents both comply because nobody ships without building.
2. **Tracer bullets over layer cake.** Build one screen end-to-end (NavKey → ViewModel → Screen → tests) before expanding. Never build all models, then all DAOs, then all screens.
3. **Start supervised, graduate to autonomous.** Begin in HITL (human-in-the-loop) mode to refine prompts. Move to AFK (away-from-keyboard) once the loop produces clean commits.

## Two Scenarios

### Greenfield (New App)

Follow the 7 phases below in order. Start with Phase 1.

### Brownfield (Existing App)

Start with **Phase 0: Diagnose** to find architectural friction. Then use the 7 phases for each improvement. See the [brownfield guide](references/brownfield.md) for the full incremental migration strategy.

## The Phases

```
Phase 0: Diagnose ──→ Where does the architecture hurt? (brownfield only)
Phase 1: Idea ──────→ What are we building? Why?
Phase 2: Research ──→ What don't we know? (optional, sprint-scoped)
Phase 3: Prototype ─→ What should it look/feel like? (optional)
Phase 4: PRD ───────→ Describe the end state
Phase 5: Plan ──────→ Break into agent-executable tasks
Phase 6: Execute ───→ Ralph loops (HITL → AFK)
Phase 7: QA ────────→ Agent QA plan + human testing → new tickets → Phase 5
```

Phases 5-7 iterate until quality is satisfactory.

### Phase 0: Diagnose (Brownfield)

Before improving anything, find out what hurts. Walk the codebase looking for friction:

**Shallow modules** — Apply the deletion test: imagine deleting this module. If complexity reappears across N callers, it was earning its keep (deep). If complexity just vanishes or callers would have the same code inline, it's a pass-through (shallow). Shallow modules to look for in Android:
- Repositories wrapping a single DAO with zero transformation
- UseCases calling a single repository method
- Manager/Helper classes that just delegate

**Missing boundaries** — Everything in one module, no separation between features, feature code mixed into core libraries.

**Tangled dependencies** — Circular deps, features depending on other features' internals, core modules depending on features.

**No test seams** — Can't test a ViewModel without its real repository, can't test a repository without the network.

**Output:** A ranked list of friction points, each with: what's wrong, why it matters, what fixing it looks like. This becomes input for Phase 1 (the improvement becomes the "idea").

### Phase 1: Idea

Refine the idea through structured questioning. Don't jump to implementation.

Ask (or have an agent ask) these questions:
- What user problem does this solve?
- What screens/flows are involved?
- What data does it need? Where does it come from?
- What's the offline story?
- What are the edge cases?
- What does "done" look like?

Output: A 1-paragraph problem statement and a list of acceptance criteria.

### Phase 2: Research (Optional)

For features involving unfamiliar APIs, libraries, or patterns. Create a `RESEARCH.md` in the repo root that caches findings for agents across context windows.

```markdown
# Research: {Feature}

## Questions
- How does {library} handle {concern}?
- What's the recommended pattern for {thing}?

## Findings
- {library} uses {pattern}. See: {official doc URL}
- Constraint: {limitation} means we must {workaround}

## Decision
Use {approach} because {reason}.
```

**Delete RESEARCH.md after the feature ships.** It goes stale fast and will mislead future agents.

### Phase 3: Prototype (Optional)

For features where design taste matters (UI-heavy screens, interactions, animations). Build throwaway variations, pick the winner, commit it so agents can reference it during implementation.

### Phase 4: PRD

Describe the **end state**, not the journey. Focus on what users see and how the system behaves. Use the [PRD template](assets/PRD-TEMPLATE.md).

A good Android PRD includes:
- User stories with acceptance criteria
- Screen descriptions (what's visible in each state)
- Data requirements (models, sources, offline behavior)
- Navigation flow
- Edge cases and error states

A bad PRD: lists implementation steps, names specific classes, prescribes architecture.

### Phase 5: Plan

Break the PRD into agent-executable tasks. Each task should be:
- **Atomic** — completable in one agent iteration (30-45 min)
- **Vertically sliced** — touches all layers (model → DAO → repository → ViewModel → screen → test)
- **Ordered by risk** — architectural/integration work first, polish last
- **Independently testable** — has a verification step

Use the [task template](assets/TASK-TEMPLATE.md). Output goes into `PRD.md` as a checklist or into GitHub Issues.

**Task ordering for Android features (tracer bullet sequence):**

```
1. Data model + entity + mapping        (core:model + core:database)
2. DAO + repository interface            (core:database + core:data)
3. Repository implementation + sync      (core:data)
4. NavKey + feature module scaffold      (feature:X:api + feature:X:impl)
5. ViewModel + sealed UI state           (feature:X:impl)
6. Screen composable (happy path only)   (feature:X:impl)
7. Wire navigation in app module         (app)
8. Tests (ViewModel + DAO)               (test)
9. Error/loading/empty states            (feature:X:impl)
10. Screenshot tests                     (test)
```

Each task produces a buildable, testable increment. Task 6 is your **tracer bullet** — one screen working end-to-end.

### Phase 6: Execute (Ralph Loop)

The implementation loop. An agent reads the PRD, picks the next task, implements it, runs feedback loops, commits, updates progress. See [Ralph scripts reference](references/ralph-scripts.md).

**Two modes:**

| Mode | Script | When | Safety |
|------|--------|------|--------|
| HITL | `scripts/ralph-once.sh` | First 2-3 iterations, risky tasks | Human watches |
| AFK | `scripts/afk-ralph.sh` | Remaining tasks after HITL proves stable | OrbStack/Docker sandbox |

**Android feedback loops (the quality gate):**

```bash
./gradlew architectureGuard     # Module structure (< 5s)
./gradlew spotlessCheck         # Formatting (< 20s)
./gradlew testDemoDebug         # Unit tests (2-5 min)
./gradlew lintProdRelease       # Lint + custom rules (2-3 min)
```

The agent must run these before committing. If any fail, the agent fixes and retries. Pre-commit hooks enforce this even if the agent forgets.

### Phase 7: QA

After execution completes:
1. Agent generates a QA plan (test scenarios, edge cases to verify manually)
2. Human tests on device/emulator
3. Issues found become new tasks → back to Phase 5
4. Iterate until quality bar is met

## Skill Mapping

Which android-* skill to load for each phase:

| Phase | Skills |
|-------|--------|
| 1-4 (Idea → PRD) | This skill's templates |
| 5 (Plan) | [android-architect](../android-architect/SKILL.md) for patterns, [android-modularizer](../android-modularizer/SKILL.md) for module structure |
| 6 (Execute) | [android-architect](../android-architect/SKILL.md), [android-build-logic](../android-build-logic/SKILL.md), [android-offline-sync](../android-offline-sync/SKILL.md), [android-compose-designsystem](../android-compose-designsystem/SKILL.md) — loaded on demand per task |
| 6 (Verify) | [android-testing](../android-testing/SKILL.md), [android-architecture-guard](../android-architecture-guard/SKILL.md) |
| 7 (QA) | [android-testing](../android-testing/SKILL.md), [android-lint-enforcement](../android-lint-enforcement/SKILL.md) |

## Sandbox Strategy for Android

Android's toolchain is too heavy for throwaway containers. Use a hybrid approach:

**Code isolation:** Git worktrees (instant, zero overhead)
**Process isolation (AFK only):** OrbStack container with host SDK bind-mounted
**Quality gates:** Gradle tasks running on host tools

See [sandbox setup reference](references/sandbox-setup.md) for the full configuration.

## Task Dispatch: Two Modes

### Recommended: GitHub Issues

Tasks live as GitHub Issues with triage labels. Durable, shareable, auditable. Supports parallel agents on separate issues.

```bash
# Setup labels (one-time)
gh label create "ready-for-agent" --color "0E8A16" --description "Fully specified, agent can implement AFK"
gh label create "ready-for-human" --color "1D76DB" --description "Requires human implementation"
gh label create "prd" --color "5319E7" --description "Product requirements document"
gh label create "afk" --color "0E8A16" --description "AFK-safe task"
gh label create "hitl" --color "E4E669" --description "Human-in-the-loop task"
```

**Pipeline:**
1. Draft PRD → publish as GitHub Issue with `prd` label
2. Break into task issues with acceptance criteria, blocking relationships, `ready-for-agent` + `afk`/`hitl` labels
3. Agent picks up issues, posts progress as comments, closes when done

**Scripts:**
```bash
# Work on a specific issue
bash skills/android-dev-workflow/scripts/ralph-issue.sh 42

# Auto-pick next ready-for-agent issue
bash skills/android-dev-workflow/scripts/ralph-issue.sh next

# Continuously process all ready-for-agent issues
bash skills/android-dev-workflow/scripts/ralph-dispatch.sh
```

See [Issue Dispatch reference](references/issue-dispatch.md) for full setup, issue templates, Agent Brief format, and parallel dispatch.

### Alternative: Local Files

For solo work, quick experiments, or repos without GitHub. Tasks tracked in `PRD.md` checklist + `progress.txt`.

```bash
cp skills/android-dev-workflow/assets/PRD-TEMPLATE.md PRD.md
touch progress.txt
bash skills/android-dev-workflow/scripts/ralph-once.sh     # HITL
bash skills/android-dev-workflow/scripts/afk-ralph.sh 10   # AFK
```

`progress.txt` persists memory across context windows. Delete after the feature ships.

## Quick Start (Issue-Based)

```bash
# 1. Setup labels (one-time)
bash skills/android-dev-workflow/scripts/setup-labels.sh

# 2. Create PRD from template, publish as issue
cp skills/android-dev-workflow/assets/PRD-TEMPLATE.md PRD.md
# Edit PRD.md, then:
gh issue create --title "PRD: {Feature}" --label "prd" --body "$(cat PRD.md)"

# 3. Break into task issues (manually or via agent)
# Each issue: acceptance criteria, blocked-by refs, ready-for-agent label

# 4. HITL: work on first issue supervised
bash skills/android-dev-workflow/scripts/ralph-issue.sh next

# 5. AFK: dispatch remaining issues automatically
bash skills/android-dev-workflow/scripts/ralph-dispatch.sh
```

## Reference Guides

- [Brownfield Migration](references/brownfield.md) — Diagnose, enforce, extract incrementally, deepen modules
- [Issue Dispatch](references/issue-dispatch.md) — GitHub Issues pipeline, triage labels, Agent Briefs, parallel dispatch
- [Ralph Scripts](references/ralph-scripts.md) — HITL and AFK loop scripts with Android feedback
- [Sandbox Setup](references/sandbox-setup.md) — OrbStack/Docker config for Android AFK runs
- [Tracer Bullets](references/tracer-bullets.md) — Vertical slice discipline for Android features
