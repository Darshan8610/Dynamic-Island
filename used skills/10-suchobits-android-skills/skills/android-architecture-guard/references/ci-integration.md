# CI Integration

## GitHub Actions

Add architecture guard as the first verification step — it's the fastest and produces the clearest errors.

```yaml
# .github/workflows/build.yml

name: Build

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      # Step 1: Architecture (< 10 seconds)
      - name: Architecture guard
        run: ./gradlew architectureGuard

      # Step 2: Formatting (< 30 seconds)
      - name: Check formatting
        run: ./gradlew spotlessCheck

      # Step 3: Lint (1-3 minutes)
      - name: Lint
        run: ./gradlew :app:lintProdRelease :lint:test

      # Step 4: Unit tests (2-5 minutes)
      - name: Unit tests
        run: ./gradlew testDemoDebug

      # Step 5: Screenshot verification (3-5 minutes)
      - name: Verify screenshots
        run: ./gradlew verifyRoborazziDemoDebug

      # Upload lint SARIF for GitHub security tab
      - name: Upload SARIF
        if: always()
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: app/build/reports/lint-results-prodRelease.sarif
```

### Why This Order

Each step is ordered by speed and signal quality:

| Step | Time | What It Catches |
|------|------|-----------------|
| Architecture guard | ~5s | Module structure, dependency direction |
| Spotless | ~20s | Formatting, license headers |
| Lint | ~2min | Source-level violations, design system bypass |
| Unit tests | ~3min | Logic errors, regressions |
| Screenshots | ~4min | Visual regressions |

Fail fast on cheap checks. Don't waste 10 minutes of test time to discover a module is in the wrong directory.

## Branch Protection

Configure branch protection to require the architecture guard:

```
Repository Settings → Branches → Branch protection rules → main
  ✓ Require status checks to pass before merging
    ✓ Architecture guard
    ✓ Unit tests
    ✓ Lint
```

## Suppression Audit

Add a periodic CI job that reports active suppressions:

```yaml
# .github/workflows/architecture-audit.yml

name: Architecture Audit

on:
  schedule:
    - cron: '0 9 * * 1'  # Every Monday at 9am

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Find active suppressions
        run: |
          echo "## Active Architecture Guard Suppressions" >> $GITHUB_STEP_SUMMARY
          echo "" >> $GITHUB_STEP_SUMMARY

          SUPPRESSIONS=$(grep -r "architectureGuard.suppress" --include="*.kts" . || true)

          if [ -z "$SUPPRESSIONS" ]; then
            echo "None found ✓" >> $GITHUB_STEP_SUMMARY
          else
            echo '```' >> $GITHUB_STEP_SUMMARY
            echo "$SUPPRESSIONS" >> $GITHUB_STEP_SUMMARY
            echo '```' >> $GITHUB_STEP_SUMMARY
            echo "" >> $GITHUB_STEP_SUMMARY
            echo "Review these suppressions. Each should be tied to a ticket and have an expiration plan." >> $GITHUB_STEP_SUMMARY
          fi

      - name: Module structure report
        run: |
          echo "## Module Structure" >> $GITHUB_STEP_SUMMARY
          echo "" >> $GITHUB_STEP_SUMMARY
          echo "### Features" >> $GITHUB_STEP_SUMMARY
          find feature -name "build.gradle.kts" -maxdepth 3 | sort | while read f; do
            echo "- $(dirname $f)" >> $GITHUB_STEP_SUMMARY
          done
          echo "" >> $GITHUB_STEP_SUMMARY
          echo "### Core" >> $GITHUB_STEP_SUMMARY
          find core -name "build.gradle.kts" -maxdepth 2 | sort | while read f; do
            echo "- $(dirname $f)" >> $GITHUB_STEP_SUMMARY
          done
```

## PR Comment on Violation

For even more visibility, add a PR comment when architecture guard fails:

```yaml
- name: Comment on PR
  if: failure() && github.event_name == 'pull_request'
  uses: actions/github-script@v7
  with:
    script: |
      github.rest.issues.createComment({
        issue_number: context.issue.number,
        owner: context.repo.owner,
        repo: context.repo.repo,
        body: '🏗️ **Architecture Guard failed.** Run `./gradlew architectureGuard` locally to see the violations.\n\nSee [architecture rules](skills/android-architecture-guard/SKILL.md) for guidance.'
      })
```

## Metrics Dashboard

Track architecture health over time by logging violations in CI:

```yaml
- name: Architecture metrics
  if: always()
  run: |
    SUPPRESSION_COUNT=$(grep -r "architectureGuard.suppress" --include="*.kts" . | wc -l || echo 0)
    MODULE_COUNT=$(find . -name "build.gradle.kts" -not -path "*/build/*" | wc -l)
    FEATURE_COUNT=$(find feature -name "build.gradle.kts" -maxdepth 3 2>/dev/null | wc -l || echo 0)
    CORE_COUNT=$(find core -name "build.gradle.kts" -maxdepth 2 2>/dev/null | wc -l || echo 0)

    echo "architecture_suppressions=$SUPPRESSION_COUNT" >> $GITHUB_OUTPUT
    echo "total_modules=$MODULE_COUNT" >> $GITHUB_OUTPUT
    echo "feature_modules=$FEATURE_COUNT" >> $GITHUB_OUTPUT
    echo "core_modules=$CORE_COUNT" >> $GITHUB_OUTPUT
```
