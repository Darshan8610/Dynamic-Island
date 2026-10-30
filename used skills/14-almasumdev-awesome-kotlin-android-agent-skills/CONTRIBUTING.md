# Contributing

Thanks for your interest in improving these agent skill collections. The same guide is used across every repository in the [awesome-agent-skills hub](https://github.com/Masum-MSNR/awesome-agent-skills).

## Ways to contribute

- **New skill** — add a `SKILL.md` under `.github/skills/<category>/<skill-name>/`.
- **Improve an existing skill** — sharper instructions, better code samples, clearer checklists.
- **Fix bugs** — incorrect APIs, broken links, outdated conventions.
- **Docs** — README, Agent.md, or category overviews.

## Skill file format

Every skill is a single `SKILL.md` with this structure:

```markdown
---
name: short-skill-id
description: One-sentence summary of what the skill does.
category: feature-area
stack: [flutter] # or [ios], [android-kotlin], [cross-platform], etc.
---

# Human-readable Title

## Instructions

1. First numbered step.
2. Second numbered step.
3. ...

### Code sample

```<lang>
// minimal, runnable, real-world example
```

## Checklist

- [ ] Item 1
- [ ] Item 2
```

### Requirements for a good skill

- **Focused** — one skill, one outcome.
- **Self-contained** — readable without other skills.
- **Real code** — no pseudocode; prefer idiomatic, current-API examples.
- **No emojis** in skill content.
- **Checklist at the end** so the agent can self-verify.

## Workflow

1. Fork the repository.
2. Create a branch: `feat/<skill-name>` or `fix/<skill-name>`.
3. Make your changes.
4. Run a quick sanity pass:
   - Frontmatter is valid YAML.
   - Code samples compile / lint in their language.
   - Links are not broken.
5. Open a pull request with a clear title and a short rationale.

## Commit style

- Keep commits small and scoped.
- Use imperative mood: `Add foo skill`, `Fix bar link`.
- One logical change per commit when practical.

## Code of conduct

Be respectful. Assume good intent. Keep discussions technical.

## License

By contributing, you agree that your contributions are licensed under:

- [MIT](LICENSE-CODE) for code samples
- [CC BY 4.0](LICENSE-DOCS) for prose and documentation
