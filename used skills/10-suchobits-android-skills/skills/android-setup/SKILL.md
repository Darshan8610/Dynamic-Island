---
name: android-setup
description: >-
  One-time setup for Android agent skills in a project. Use when initializing android skills
  in a repo, configuring the issue tracker (GitHub, GitLab, or local markdown), setting up
  triage labels, or scaffolding docs/agents/ config files. Run this before using
  android-dev-workflow, ralph-issue, or ralph-dispatch.
license: Apache-2.0
compatibility: Requires git. Optional gh CLI (GitHub) or glab CLI (GitLab).
metadata:
  author: luis-suchobits
  version: "1.0"
  based-on: mattpocock/skills
---

# Android Setup — Project Configuration

One-time setup that scaffolds the configuration other android-* skills depend on. Run this once per repo before using `android-dev-workflow`, `ralph-issue.sh`, or `ralph-dispatch.sh`.

## What Gets Created

```
docs/agents/
├── issue-tracker.md       # Which tracker + CLI commands
└── triage-labels.md       # Label name mappings
```

Plus an `## Agent skills` block in your `CLAUDE.md` or `AGENTS.md` pointing to these files.

## Setup Flow

### Phase 1: Explore

Read the repo's current state:
- `git remote -v` — detect GitHub vs GitLab vs other
- Existing `CLAUDE.md` / `AGENTS.md` — check for prior config
- Existing `docs/agents/` — check if already set up
- `.scratch/` — check for local issue tracking

If `docs/agents/issue-tracker.md` already exists, ask the user if they want to reconfigure or keep it.

### Phase 2: Issue Tracker Selection

Detect the tracker from the git remote and confirm with the user.

**Auto-detection rules:**
- Remote contains `github.com` → propose GitHub
- Remote contains `gitlab` → propose GitLab
- No remote / other → propose Local Markdown

Present the options:
1. **GitHub** — Uses `gh` CLI. Requires: `gh auth login` completed.
2. **GitLab** — Uses `glab` CLI. Requires: `glab auth login` completed.
3. **Local Markdown** — Uses `.scratch/` directory. No CLI needed. Good for solo projects or when the repo has no remote issue tracker.
4. **Other** — User describes their workflow in one paragraph. Stored as freeform prose.

### Phase 3: Triage Labels

Present the five canonical triage roles with default label names. Ask the user to confirm or customize:

| Role | Default Label | Purpose |
|------|--------------|---------|
| Ready for agent | `ready-for-agent` | Fully specified, agent can implement AFK |
| Ready for human | `ready-for-human` | Requires human implementation |
| Needs triage | `needs-triage` | Maintainer needs to evaluate |
| Needs info | `needs-info` | Waiting on reporter input |
| Won't fix | `wontfix` | Will not be actioned |
| PRD | `prd` | Product requirements document |
| AFK | `afk` | AFK-safe task |
| HITL | `hitl` | Human-in-the-loop task |

If the user's team already has labels (e.g., `agent-ready` instead of `ready-for-agent`), map to their existing names.

### Phase 4: Write Config

Create `docs/agents/` directory and write the config files.

**`docs/agents/issue-tracker.md`** — Write from the appropriate template:

For GitHub:
```markdown
# Issue Tracker: GitHub

tracker: github

## CLI Reference

- Create issue: `gh issue create --title "..." --body "..." --label "..."`
- View issue: `gh issue view <number>`
- View with comments: `gh issue view <number> --json comments`
- List by label: `gh issue list --label "ready-for-agent" --state open --json number,title,body`
- Add label: `gh issue edit <number> --add-label "label"`
- Remove label: `gh issue edit <number> --remove-label "label"`
- Close issue: `gh issue close <number>`
- Comment: `gh issue comment <number> --body "..."`
```

For GitLab:
```markdown
# Issue Tracker: GitLab

tracker: gitlab

## CLI Reference

- Create issue: `glab issue create --title "..." --description "..." --label "..."`
- View issue: `glab issue view <number>`
- List by label: `glab issue list --label "ready-for-agent"`
- Add label: `glab issue update <number> --label "label"`
- Remove label: `glab issue update <number> --unlabel "label"`
- Close issue: `glab issue close <number>`
- Comment: `glab issue note <number> --message "..."`

## Notes
- GitLab calls comments "notes"
- GitLab calls PRs "merge requests"
```

For Local:
```markdown
# Issue Tracker: Local Markdown

tracker: local

## Convention

- PRDs: `.scratch/{feature-slug}/PRD.md`
- Issues: `.scratch/{feature-slug}/issues/{NN}-{slug}.md`
- Status tracked as `Status:` line in each file (open/closed)
- Comments appended under `## Comments` heading
- No CLI required
```

**`docs/agents/triage-labels.md`** — Write the label mapping table:

```markdown
# Triage Labels

| Role | Label | Description |
|------|-------|-------------|
| ready-for-agent | {user's label} | Fully specified, agent can implement AFK |
| ready-for-human | {user's label} | Requires human implementation |
| needs-triage | {user's label} | Maintainer needs to evaluate |
| needs-info | {user's label} | Waiting on reporter input |
| wontfix | {user's label} | Will not be actioned |
| prd | {user's label} | Product requirements document |
| afk | {user's label} | AFK-safe task |
| hitl | {user's label} | Human-in-the-loop task |
```

### Phase 5: Update CLAUDE.md / AGENTS.md

Add or update an `## Agent skills` block:

```markdown
## Agent skills

### Issue Tracker
See [docs/agents/issue-tracker.md](docs/agents/issue-tracker.md) for CLI commands and conventions.

### Triage Labels
See [docs/agents/triage-labels.md](docs/agents/triage-labels.md) for label mappings.
```

If `CLAUDE.md` exists, update it. If `AGENTS.md` exists, update that. If neither exists, ask the user which to create.

### Phase 6: Create Labels

Ask the user: *"Create the triage labels in your tracker now?"*

If yes, run:
```bash
bash skills/android-dev-workflow/scripts/setup-labels.sh
```

### Phase 7: Done

Report what was created and tell the user:
- They can edit `docs/agents/*.md` directly at any time
- Run `ralph-issue.sh next` to start working on issues
- Run `ralph-dispatch.sh` for autonomous processing

## Hard Dependencies

These skills **require** setup to function:
- `android-dev-workflow` (ralph-issue.sh, ralph-dispatch.sh, setup-labels.sh)

These skills **work without** setup but benefit from it:
- `android-architect`, `android-modularizer`, `android-testing`, etc. (pattern skills don't need a tracker)

## Adding a New Tracker

To add support for a tracker not listed above (e.g., Linear, Jira):

1. Copy the adapter template:
   ```bash
   cp skills/android-dev-workflow/scripts/lib/tracker-template.sh \
      skills/android-dev-workflow/scripts/lib/tracker-{name}.sh
   ```

2. Implement every function in the template using your tracker's CLI or API.

3. Create `docs/agents/issue-tracker.md` with `tracker: {name}` in the header.

4. Test: `bash skills/android-dev-workflow/scripts/setup-labels.sh`

See [tracker-template.sh](../android-dev-workflow/scripts/lib/tracker-template.sh) for the full interface contract with documentation for every function.
