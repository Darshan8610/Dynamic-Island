# Task: {Short Description}

## Context
{Which PRD slice this belongs to. What was completed in the previous iteration.}

## Objective
{One sentence: what this task produces when done.}

## Acceptance Criteria
- [ ] {Observable outcome 1}
- [ ] {Observable outcome 2}
- [ ] All feedback loops pass: architectureGuard, spotlessCheck, testDemoDebug

## Files to Create/Modify
- `{module}/src/main/kotlin/.../FileName.kt` — {what it does}
- `{module}/src/test/kotlin/.../FileNameTest.kt` — {what it tests}

## Tracer Bullet Constraint
This task must touch all relevant layers. Do not leave wiring or tests for a later task.

## Verification
```bash
./gradlew architectureGuard spotlessCheck testDemoDebug
```

## Notes
{Any decisions from RESEARCH.md, prototype learnings, or previous iteration context.}
