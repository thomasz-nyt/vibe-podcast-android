# Spec 001: Ensure Gradle runs on JDK 17

## Context
The project targets Java 17 (`compileOptions` + `kotlinOptions`). However, Gradle itself runs on whatever JDK is selected by the environment.

On machines where `java` points at a very new JDK (e.g. **JDK 25**), Gradle/Kotlin DSL can fail early during settings/build script compilation (example seen on this dev machine):

- `java.lang.IllegalArgumentException: 25.0.1` (from Kotlin/IntelliJ `JavaVersion.parse`)

This makes the repo effectively **not buildable out-of-the-box** for some environments.

## Goals
- Make it easy/obvious to use **JDK 17** locally.
- Keep CI pinned to **JDK 17**.

## Non-goals
- Upgrading Gradle/AGP/Kotlin as part of this change.
- Trying to support every local setup automatically.

## Proposal
1. Add a repo-level toolchain hint file so common version managers (asdf/mise) can select a compatible JDK.
2. Document the requirement in README.

### Implementation
- Add `.tool-versions` with:
  - `java 17`
- README: note that the project expects JDK 17 and how to set `JAVA_HOME` accordingly.

## Acceptance criteria
- CI uses JDK 17 (already true).
- Local dev can run `mise install` (or equivalent) and then `./gradlew assembleDebug` successfully.

## Testing checklist
- [ ] With system `java` pointing at a newer JDK (>= 21), set JDK 17 via mise/asdf or `JAVA_HOME`.
- [ ] `./gradlew assembleDebug` succeeds.
