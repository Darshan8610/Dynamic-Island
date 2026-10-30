# Sandbox Setup for Android

## The Android Container Problem

Android's toolchain doesn't fit the "disposable container" model:

| Component | Size | Notes |
|-----------|------|-------|
| Android SDK (platform + build tools) | ~2GB | Minimum for command-line builds |
| Gradle wrapper + caches | ~1-3GB | Grows with project dependencies |
| JDK 17 | ~300MB | Required for Gradle |
| Android emulator + system image | ~2-4GB | Needs KVM for reasonable speed |

A full Android dev container is 5-8GB. Building it takes 10-15 minutes. That's untenable for ephemeral sandboxes.

## The Hybrid Approach

Use **git worktrees** for code isolation and **bind-mount host tools** into containers.

```
Host Machine (OrbStack/Docker)
├── Android SDK         ← bind-mounted read-only into container
├── Gradle cache        ← bind-mounted read-only into container
├── Git repo
│   ├── working tree    ← main development
│   └── .worktrees/
│       └── ralph-001/  ← isolated worktree for agent
│           └── (bind-mounted into container as /workspace)
└── OrbStack container
    ├── /workspace      ← worktree mount (read-write)
    ├── /android-sdk    ← host SDK mount (read-only)
    ├── /gradle-cache   ← host cache mount (read-only)
    └── claude CLI      ← installed in container
```

The container provides process isolation (the agent can't `rm -rf /`). The worktree provides code isolation (the agent's changes don't affect your working tree). The host tools avoid duplicating 5GB.

## OrbStack Setup (Recommended for macOS)

OrbStack is a lightweight Docker-compatible runtime for macOS. It's faster and uses less memory than Docker Desktop.

### 1. Install OrbStack

```bash
brew install orbstack
```

### 2. Create the Android Ralph Image

```dockerfile
# .ralph/Dockerfile
FROM ubuntu:24.04

# System dependencies
RUN apt-get update && apt-get install -y \
    curl git jq unzip openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64

# Install Claude Code CLI
RUN curl -fsSL https://claude.ai/install.sh | bash

# Create agent user (UID alignment with host)
ARG HOST_UID=501
ARG HOST_GID=20
RUN groupadd -g $HOST_GID agent 2>/dev/null || true \
    && useradd -m -u $HOST_UID -g $HOST_GID agent

USER agent
WORKDIR /workspace

# Android SDK and Gradle cache are bind-mounted from host
ENV ANDROID_HOME=/android-sdk
ENV GRADLE_USER_HOME=/gradle-cache
ENV PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

ENTRYPOINT ["sleep", "infinity"]
```

### 3. Build the Image

```bash
# Get your UID/GID for permission alignment
docker build -t android-ralph:latest \
    --build-arg HOST_UID=$(id -u) \
    --build-arg HOST_GID=$(id -g) \
    -f .ralph/Dockerfile .
```

### 4. Run AFK Ralph in Sandbox

```bash
bash skills/android-dev-workflow/scripts/afk-ralph.sh 10 --sandbox
```

The script handles container creation, bind-mounting, and cleanup.

## Docker Desktop Setup (Alternative)

Same Dockerfile and commands. Replace `orbstack` with Docker Desktop. Note: Docker Desktop uses more RAM and has slower file I/O on macOS (OrbStack uses VirtioFS natively).

## Worktree-Only Mode (No Container)

For HITL runs or when containers aren't available, use worktrees without containers:

```bash
# Create an isolated worktree
git worktree add .worktrees/ralph-$(date +%s) -b ralph/feature-x

# Run ralph in the worktree
cd .worktrees/ralph-*/
bash ../../skills/android-dev-workflow/scripts/ralph-once.sh

# Merge back when done
cd ../..
git merge ralph/feature-x
git worktree remove .worktrees/ralph-*/
git branch -d ralph/feature-x
```

The `afk-ralph.sh` script does NOT auto-create worktrees — it runs in the current directory. Create a worktree manually for code isolation without containers.

## Environment Variables

| Variable | Required | Default | Notes |
|----------|----------|---------|-------|
| `ANTHROPIC_API_KEY` | Yes (AFK) | — | Claude API key |
| `ANDROID_HOME` | For sandbox | `$HOME/Library/Android/sdk` | Host Android SDK path |
| `GRADLE_USER_HOME` | For sandbox | `$HOME/.gradle` | Host Gradle cache path |

### .env File

```bash
# .ralph/.env (gitignored)
ANTHROPIC_API_KEY=sk-ant-...
```

## Sandcastle Comparison

If you're using Sandcastle for TypeScript projects and want the same model for Android:

| Sandcastle Concept | Android Equivalent |
|----|---|
| `docker()` sandbox provider | OrbStack container with Android SDK bind-mount |
| `sandcastle.run()` | `afk-ralph.sh` bash loop |
| `sandcastle.createWorktree()` | `git worktree add` |
| `.sandcastle/Dockerfile` | `.ralph/Dockerfile` |
| `.sandcastle/prompt.md` | Inline prompt in ralph scripts |
| `{{KEY}}` prompt substitution | `@file` references in Claude CLI |
| `<promise>COMPLETE</promise>` | Same — identical signal |
| `onSandboxReady` hook | Pre-commit hook + architectureGuard |
| Branch strategy: merge-to-head | `git merge` from worktree branch |

The key difference: Sandcastle is a TypeScript library with programmatic orchestration. Our approach is bash scripts with CLI invocations — simpler, no Node dependency, works with any AI CLI.

## Gradle Daemon Considerations

When bind-mounting Gradle cache read-only, the Gradle daemon can't write lock files. Two options:

### Option A: Writeable Gradle Home (Recommended)

Mount a per-worktree Gradle home that inherits from the host cache:

```bash
# Create a writeable Gradle home that references host cache
mkdir -p .worktrees/ralph-001/.gradle
echo "org.gradle.dependency.verification=off" > .worktrees/ralph-001/.gradle/gradle.properties

docker run --rm \
    -v "$REPO_ROOT/.worktrees/ralph-001:/workspace" \
    -v "${ANDROID_HOME}:/android-sdk:ro" \
    -v "$REPO_ROOT/.worktrees/ralph-001/.gradle:/home/agent/.gradle" \
    -e ANDROID_HOME=/android-sdk \
    android-ralph:latest \
    claude -p "$PROMPT"
```

### Option B: Disable Daemon in Container

```bash
# In the container, run Gradle without daemon
./gradlew --no-daemon testDemoDebug
```

Slower but avoids lock file issues entirely.

## Security Notes

- **Never mount `~/.ssh` or `~/.config/gh`** into the container unless the agent needs git push or GitHub access.
- **API keys via env vars, not files.** Pass `ANTHROPIC_API_KEY` as `-e`, don't mount `~/.anthropic`.
- **Read-only SDK mount.** The agent should not modify the host Android SDK.
- **Iteration caps are cost controls.** Each iteration costs $0.50-2.00 in API calls. 50 iterations = $25-100.
