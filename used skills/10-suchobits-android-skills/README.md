# Android Skills

Agent skills for building and maintaining production-grade Android apps. Works with any AI coding agent — [Claude Code](https://claude.ai/code), [Cursor](https://cursor.com), [Copilot](https://github.com/features/copilot), [Gemini CLI](https://geminicli.com), [Codex](https://developers.openai.com/codex), [Junie](https://junie.jetbrains.com), and [others](https://agentskills.io/clients).

Built on patterns from Google's [Now in Android](https://github.com/android/nowinandroid) reference app, the [Agent Skills](https://agentskills.io) open standard, and autonomous development patterns from [aihero.dev](https://www.aihero.dev) and [mattpocock/skills](https://github.com/mattpocock/skills).

## What's In The Box

**9 skills, 43 files, 8,670 lines** covering the full Android development lifecycle:

### Architecture & Patterns

| Skill | What It Does |
|-------|-------------|
| [android-architect](skills/android-architect/SKILL.md) | Core architecture patterns — Clean Architecture, MVVM, Repository, Compose UI, StateFlow, Hilt DI, Navigation |
| [android-modularizer](skills/android-modularizer/SKILL.md) | Multi-module project structure — API/impl splits, dependency rules, module extraction from monoliths |
| [android-build-logic](skills/android-build-logic/SKILL.md) | Gradle convention plugins, version catalogs, build variants, build performance |

### Domain-Specific

| Skill | What It Does |
|-------|-------------|
| [android-offline-sync](skills/android-offline-sync/SKILL.md) | Offline-first sync with WorkManager — Syncable/Synchronizer, delta sync, network monitoring |
| [android-compose-designsystem](skills/android-compose-designsystem/SKILL.md) | Material 3 design system module — theme, wrapped components, dynamic color, icon system |

### Quality & Enforcement

| Skill | What It Does |
|-------|-------------|
| [android-testing](skills/android-testing/SKILL.md) | Full testing strategy — unit, UI, screenshot (Roborazzi), DAO, benchmarks, baseline profiles |
| [android-lint-enforcement](skills/android-lint-enforcement/SKILL.md) | Custom lint rules, design system enforcement, Spotless/ktlint, SARIF CI integration |
| [android-architecture-guard](skills/android-architecture-guard/SKILL.md) | Build-time enforcement — Gradle task, pre-commit hook, CI pipeline. Makes violations unbuildable |

### Workflow & Automation

| Skill | What It Does |
|-------|-------------|
| [android-dev-workflow](skills/android-dev-workflow/SKILL.md) | Full lifecycle orchestration — 7-phase pipeline, Ralph loops, issue dispatch, tracer bullets, OrbStack sandboxing |

## Getting Started

### Install

#### One-liner (auto-detects your agents)

```bash
curl -fsSL https://raw.githubusercontent.com/suchobits/android-skills/main/install.sh | bash
```

The installer downloads the skills, detects which AI agents you have installed (Claude Code, Gemini CLI, Codex, Cursor, Junie, Windsurf, Roo, OpenCode, Goose), and copies skills to the correct directory for each. Skills are installed into the **current project** by default.

**Options** (via environment variables):

```bash
# Install to user-level (available in all projects)
curl -fsSL ... | SCOPE=user bash

# Install for specific agents only
curl -fsSL ... | AGENTS=claude,codex bash

# Install from a specific branch
curl -fsSL ... | BRANCH=dev bash
```

#### Manual Install

Most agents scan **`.agents/skills/`** as the cross-agent standard. Claude Code is the exception — it only scans `.claude/skills/`.

```bash
git clone https://github.com/suchobits/android-skills.git /tmp/android-skills

# Cross-agent (Codex, Gemini CLI, Cursor, Copilot, Goose, Roo, Windsurf, OpenCode)
cp -r /tmp/android-skills/skills/ .agents/skills/

# Claude Code (does NOT scan .agents/skills/)
cp -r /tmp/android-skills/skills/ .claude/skills/
```

For user-level install, replace `.agents/skills/` with `~/.agents/skills/` and `.claude/skills/` with `~/.claude/skills/`.

#### Where Each Agent Looks

| Agent | Project Path | User Path |
|-------|-------------|-----------|
| Claude Code | `.claude/skills/` | `~/.claude/skills/` |
| Gemini CLI | `.agents/skills/` or `.gemini/skills/` | `~/.agents/skills/` |
| OpenAI Codex | `.agents/skills/` | `~/.agents/skills/` |
| Cursor | `.agents/skills/` or `.cursor/skills/` | `~/.agents/skills/` |
| GitHub Copilot | `.agents/skills/` or `.github/skills/` | `~/.agents/skills/` |
| Junie | `.junie/skills/` | `~/.junie/skills/` |
| OpenCode | `.agents/skills/` or `.opencode/skills/` | `~/.agents/skills/` |
| Goose | `.agents/skills/` | `~/.agents/skills/` |
| Roo Code | `.agents/skills/` or `.roo/skills/` | `~/.agents/skills/` |
| Windsurf | `.agents/skills/` or `.windsurf/skills/` | `~/.agents/skills/` |

Most agents also scan `.claude/skills/` for backward compatibility. See [agentskills.io/clients](https://agentskills.io/clients) for the latest.

### Verify Installation

Ask your agent: *"What skills do you have for Android development?"*

It should list the skills relevant to what you're working on. Skills load progressively — the agent reads only the `name` and `description` at startup, then loads the full `SKILL.md` when a task matches.

---

## Scenario A: Greenfield (New App)

Starting a new Android app from scratch. The skills guide you from first module to production-ready architecture.

### 1. Scaffold the project structure

Ask your agent: *"Create a new multi-module Android project with feature modules, core libraries, and build-logic convention plugins."*

Skills activated:
- **android-architect** — module structure, dependency rules
- **android-build-logic** — convention plugins, version catalog
- **android-modularizer** — API/impl split pattern

What gets created:
```
app/
core/model/                    # Plain data classes
core/database/                 # Room entities + DAOs
core/data/                     # Repository interfaces + implementations
core/network/                  # Network data sources
core/designsystem/             # Material 3 theme + components
core/ui/                       # Shared composables
core/navigation/               # NavKey abstractions
core/common/                   # Dispatchers, extensions
core/testing/                  # Test fakes + rules
feature/{first-feature}/api/   # NavKey
feature/{first-feature}/impl/  # ViewModel + Screen
build-logic/convention/        # Convention plugins
```

### 2. Wire enforcement from day one

```bash
# Add architecture guard to root build.gradle.kts (see android-architecture-guard)
# Install pre-commit hook
cp skills/android-architecture-guard/scripts/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

Skills activated:
- **android-architecture-guard** — Gradle task, pre-commit hook
- **android-lint-enforcement** — custom lint rules, Spotless

Now violations fail the build. For both you and the agent.

### 3. Build your first feature (tracer bullet)

```bash
# Setup issue tracking
bash skills/android-dev-workflow/scripts/setup-labels.sh

# Create PRD from template
cp skills/android-dev-workflow/assets/PRD-TEMPLATE.md PRD.md
# Edit it, then publish as a GitHub Issue
gh issue create --title "PRD: Home Feed" --label "prd" --body "$(cat PRD.md)"

# Break into task issues (manually or with agent assistance)
# Each issue = one vertical slice (model → DAO → repo → ViewModel → screen → test)

# Run first iteration supervised (HITL)
bash skills/android-dev-workflow/scripts/ralph-issue.sh next

# Happy with the output? Go autonomous
bash skills/android-dev-workflow/scripts/ralph-dispatch.sh
```

Skills activated:
- **android-dev-workflow** — 7-phase pipeline, Ralph loops
- **android-architect** — screen patterns, ViewModel, repository
- **android-testing** — ViewModel tests, screenshot tests

### 4. Add more features

Repeat step 3 for each feature. The architecture guard ensures every new feature follows the established patterns.

---

## Scenario B: Brownfield (Existing App)

You have an existing app — maybe a monolith, maybe partially modularized, maybe a "ball of mud." The skills help you improve it incrementally without a rewrite.

### 1. Diagnose the current state

Ask your agent: *"Analyze this project's module structure, dependency graph, and architecture patterns. Identify friction points."*

Look for:
- **Shallow modules** — Interface nearly as complex as implementation (wrapper classes that just delegate, single-method repositories that add no value). Apply the deletion test: if you deleted this module, would complexity reappear across callers, or just vanish?
- **Circular dependencies** — Module A depends on B depends on A
- **Feature code in core modules** — Business logic in `core:data` instead of `feature:*/impl`
- **Missing boundaries** — Everything in one module, no API/impl split
- **No test seams** — Can't test a component without spinning up the entire app

### 2. Set up enforcement first

Before improving anything, set up the architecture guard so you can't make things worse:

```bash
# Install guard (even in a monolith — start with lenient rules, tighten over time)
# Add suppressions for existing violations — they become your migration backlog

# In each module's build.gradle.kts that currently violates rules:
extra["architectureGuard.suppress"] = listOf(
    "dependency-direction",  // TODO: extract shared types to core:model — tracked in #45
)
```

This lets the build pass today while making NEW violations impossible. Each suppression is visible in code review and tracked to a ticket.

Skills activated: **android-architecture-guard**

### 3. Extract incrementally (bottom-up)

Don't rewrite. Extract one module at a time, bottom-up:

```
Phase 1: Extract core:model (pure data classes — zero deps)
Phase 2: Extract core:common (dispatchers, extensions)
Phase 3: Extract core:database (Room entities + DAOs → depends on model)
Phase 4: Extract core:network (network models + data source → depends on model)
Phase 5: Extract core:data (repositories → depends on database + network)
Phase 6: Extract core:designsystem (theme + components — no business deps)
Phase 7: Split first feature into api + impl
Phase 8: Remove suppressions one by one as violations are fixed
```

Each phase is a PRD → issues → Ralph loop cycle:

```bash
# Create issues for Phase 1
gh issue create --title "Extract core:model — pure data classes" \
    --label "ready-for-agent,afk" \
    --body "## What to Build
Move all data classes that are shared across features into core:model.
core:model must have zero project dependencies.

## Acceptance Criteria
- [ ] core:model module exists with convention plugin
- [ ] All shared data classes moved
- [ ] core:model has no project dependencies
- [ ] Consumers updated to depend on core:model
- [ ] architectureGuard passes
- [ ] All tests pass"

# Agent works on it
bash skills/android-dev-workflow/scripts/ralph-issue.sh next
```

Skills activated:
- **android-modularizer** — extraction strategy, dependency rules
- **android-architect** — target patterns for each module type
- **android-dev-workflow** — issue dispatch, Ralph loops

### 4. Deepen shallow modules

After extraction, look for modules that are shallow wrappers — they exist but don't earn their keep. Use the deletion test:

*"If I deleted this repository class, would complexity reappear across 5 ViewModels (deep — keep it) or would callers just call the DAO directly with the same code (shallow — merge it)?"*

For Android specifically:
- **Repository wrapping a single DAO with no transformation** — Merge or simplify. Only keep the repository if it combines multiple sources or adds sync logic.
- **UseCase wrapping a single repository call** — Delete it. Add a use case only when combining data from multiple repositories.
- **Manager/Helper classes** — Usually shallow. Inline their logic.

Skills activated: **android-architect** (repository patterns, use case guidance)

### 5. Introduce design system enforcement

Once `core:designsystem` exists:

```bash
# Add the design system lint detector (see android-lint-enforcement)
# Existing Material usage becomes lint errors
# Fix them incrementally — each wrapped component is one issue
```

Skills activated:
- **android-compose-designsystem** — component wrapping patterns
- **android-lint-enforcement** — DesignSystemDetector setup

### 6. Add testing infrastructure

```bash
# Create core:testing with fakes and rules
# Add MainDispatcherRule
# Create test doubles for each repository
# Add Roborazzi for screenshot testing
```

Skills activated: **android-testing** (all references — unit, screenshot, UI, benchmarks)

### Brownfield Migration Checklist

- [ ] Architecture guard installed with suppressions for existing violations
- [ ] `core:model` extracted (leaf node, zero deps)
- [ ] `core:common` extracted (dispatchers, extensions)
- [ ] `core:database` extracted (Room entities + DAOs)
- [ ] `core:network` extracted (network models + data source)
- [ ] `core:data` extracted (repositories)
- [ ] `core:designsystem` extracted (theme + components)
- [ ] Design system lint detector active
- [ ] First feature split into api + impl
- [ ] `core:testing` with fakes for all repositories
- [ ] Spotless/ktlint configured
- [ ] All architecture guard suppressions resolved (zero remaining)
- [ ] CI pipeline running: architectureGuard → spotlessCheck → lint → tests → screenshots

---

## Autonomous Development Pipeline

For teams ready to run agents autonomously, the full pipeline looks like this:

```
Human                              Agent (Ralph)
──────                             ─────────────
1. Define idea                     
2. /grill-me (refine)             
3. Write PRD                       
4. Publish PRD as Issue             
5. Break into task issues ──────→  6. Pick up ready-for-agent issue
   (vertical slices, HITL/AFK)     7. Implement (tracer bullet)
                                   8. Run feedback loops (Gradle)
                                   9. Commit + close issue
                                   10. Pick next issue → repeat
                                   
11. QA review ←──────────────────  12. Post progress on issues
13. File new issues if needed ──→  14. Pick up new issues → repeat
```

### Scripts

| Script | What It Does |
|--------|-------------|
| `scripts/setup-labels.sh` | Create triage labels in your GitHub repo |
| `scripts/ralph-once.sh` | One HITL iteration (supervised) |
| `scripts/ralph-issue.sh <N>` | Work on GitHub Issue #N |
| `scripts/ralph-issue.sh next` | Auto-pick next unblocked `ready-for-agent` issue |
| `scripts/ralph-dispatch.sh` | Continuously process all `ready-for-agent` issues |
| `scripts/afk-ralph.sh <N>` | N autonomous iterations against local PRD.md |

### Sandboxing (OrbStack / Docker)

For AFK runs, use an OrbStack container with the host Android SDK bind-mounted:

```bash
bash skills/android-dev-workflow/scripts/afk-ralph.sh 10 --sandbox
```

See [sandbox setup](skills/android-dev-workflow/references/sandbox-setup.md) for the Dockerfile and configuration.

---

## Skill Reference

### Progressive Disclosure

Skills follow the [Agent Skills spec](https://agentskills.io/specification):

1. **Discovery** (~100 tokens) — Agent reads `name` + `description` from frontmatter
2. **Activation** (<5000 tokens) — Agent reads full `SKILL.md` when a task matches
3. **Resources** (on demand) — Agent reads `references/*.md` only when needed

This means all 9 skills can be installed simultaneously without overwhelming the agent's context.

### Skill Graph

Skills are independent but composable. Here's how they connect:

```
android-dev-workflow (orchestrator)
├── Phase 1-4: PRD templates, issue dispatch
├── Phase 5: ──→ android-architect (what patterns to follow)
│             ──→ android-modularizer (how to structure modules)
├── Phase 6: ──→ android-architect, android-build-logic,
│                android-offline-sync, android-compose-designsystem
│                (loaded per-task as needed)
├── Phase 6 verify: ──→ android-testing (test patterns)
│                    ──→ android-architecture-guard (enforcement)
│                    ──→ android-lint-enforcement (source-level)
└── Phase 7: ──→ android-testing (QA patterns)
```

### File Structure Per Skill

```
{skill-name}/
├── SKILL.md              # Required — frontmatter + instructions (<500 lines)
├── references/            # Optional — detailed docs loaded on demand
│   ├── topic-a.md
│   └── topic-b.md
├── scripts/               # Optional — executable automation
│   └── script.sh
└── assets/                # Optional — templates, examples
    └── TEMPLATE.md
```

---

## Contributing

### Adding a New Skill

1. Create `skills/{skill-name}/SKILL.md` with frontmatter (`name`, `description`)
2. Keep the body under 500 lines — move details to `references/`
3. Use `{Placeholder}` syntax for all code examples
4. Validate: `skills-ref validate ./skills/{skill-name}` (or use the [skill-creator](https://agentskills.io/skill-creation/quickstart))
5. Update this README

### Skill Naming

- Lowercase letters, numbers, hyphens only
- Prefix with `android-` for domain-specific skills
- Directory name must match the `name` field in frontmatter

---

## Credits

- Architecture patterns: [Now in Android](https://github.com/android/nowinandroid) (Google)
- Skill format: [Agent Skills](https://agentskills.io) (Anthropic)
- Workflow methodology: [mattpocock/skills](https://github.com/mattpocock/skills), [addyosmani/agent-skills](https://github.com/addyosmani/agent-skills)
- Autonomous loop: [Ralph Wiggum pattern](https://www.aihero.dev/getting-started-with-ralph) (aihero.dev)
- Sandbox architecture: [Sandcastle](https://github.com/mattpocock/sandcastle) (Matt Pocock)
- "Ball of mud" recovery: [A Philosophy of Software Design](https://web.stanford.edu/~ouster/cgi-bin/book.php) (Ousterhout) via mattpocock/skills

## License

Apache-2.0
