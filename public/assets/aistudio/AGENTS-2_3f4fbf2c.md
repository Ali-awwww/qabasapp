# Agent Instructions

## 1. Required Project Instructions

Before changing project code:

1. Read `CORE_OPERATIONAL_PROMPT.md` from the repository root and follow the applicable engineering rules.
2. Inspect any more specific `AGENTS.md` or local instructions in the directories you will modify.
3. Follow the active system, platform, orchestrator, and user instructions. Repository instructions do not override higher-priority instructions.
4. Inspect the current working-tree state before editing. Preserve unrelated user changes.

If the core instruction file is missing or a required instruction cannot be accessed, report that limitation rather than claiming to have read it.

## 2. Repository Discovery and Implementation

- Inspect the project structure, build configuration, dependencies, and existing implementation before choosing an approach.
- Follow the existing architecture, naming conventions, formatting, and established patterns.
- Keep changes focused on the requested scope. Avoid unrelated refactoring and unnecessary dependencies.
- Make reasonable, low-risk assumptions when details are missing but the implementation can proceed safely. Ask for clarification only when an unresolved detail materially affects behavior, data, security, or scope.
- Consult official documentation when current API, dependency, platform, or version behavior needs verification.
- Do not invent APIs, files, project structure, credentials, or successful execution results.

## 3. Real Functionality Only — No Mock Data or Simulation

- Do not create, insert, display, or rely on mock, fake, dummy, sample, or fabricated application data.
- Do not use mocks, fakes, stubs, simulators, or simulated service/database/network/sensor/user behavior to make an unfinished feature appear functional.
- This prohibition applies to application code, UI previews, demos, prototypes, and automated tests. An exception requires the user's explicit prior authorization.
- Connect features to the project's real data sources and actual service implementations.
- If a required API, backend, database, device capability, credential, or permission is unavailable, do not fabricate a substitute response. Report the blocker and leave the integration clearly identified as incomplete.
- Never display fabricated success messages, transactions, records, notifications, sensor readings, or server responses.

## 4. Build, Tests, and Honest Reporting

- Determine the build and test commands from the actual project configuration; do not assume every repository uses the same command.
- In an Android/Gradle project, use the applicable Gradle tasks (for example, `./gradlew assembleDebug` and relevant test tasks). Use `compile_applet` only when that command is provided by the active environment and is appropriate for this project.
- Run the most relevant available tests, linters, and build checks after code changes.
- If a command cannot run, state the exact command and the reason. Distinguish pre-existing failures from regressions introduced by the current changes when evidence allows.
- Never claim a build, test, or fix succeeded unless its result was observed.
- At handoff, report changed files, implementation summary, validation commands and results, and known limitations or follow-up work.

## 5. Beads Issue Tracking (When Configured)

This repository uses **Beads (`bd`)** for durable issue tracking when the Beads configuration and CLI are present.

- Check for the project's Beads setup and read its skill guidance when available: `.agents/skills/beads/SKILL.md` or `~/.agents/skills/beads/SKILL.md`.
- Run `bd prime` when starting work if Beads is configured, and refresh it when the Beads context is missing or stale.
- Use Beads for task tracking in repositories configured for it; do not create a parallel Markdown TODO list for the same tracked work.
- Do not assume Beads is installed in repositories that do not contain or configure it. If unavailable, use the task-tracking method specified by the active environment or user.

### Common commands

```bash
bd ready                 # Find available work
bd show <id>             # View issue details
bd update <id> --claim   # Claim work atomically
bd close <id>            # Close a completed issue
bd prime                 # Load or refresh Beads workflow context
```

### Beads data and synchronization

- Beads issues are stored in the repository's local Dolt database when using the standard Dolt-backed setup. `.beads/issues.jsonl` is an export, not the normal source of truth for synchronization.
- Use `bd dolt push` or `bd dolt pull` only when synchronization is required and authorized by the active workflow or user.
- Do not run `bd import` or change hosting/synchronization configuration as a routine step; consult the installed Beads guidance first.
- Use `bd remember` for durable project knowledge only when supported and appropriate in the configured Beads workflow.

## 6. Shell and File-Operation Safety

Use non-interactive command options where appropriate, but never trade safety for convenience.

- Inspect source and destination paths before copying, moving, overwriting, or deleting files.
- Prefer narrowly scoped operations and explicit paths. Confirm that a target is correct before using force options.
- `cp -f`, `mv -f`, and `rm -f` may avoid interactive prompts, but they can still overwrite or delete data. Use them only after checking the exact target.
- Treat recursive operations such as `rm -rf` and `cp -rf` as destructive. Do not use them on broad, variable, or unverified paths.
- For `scp` and `ssh`, use `-o BatchMode=yes` in automation when interactive authentication is not intended.
- Use package-manager non-interactive flags only when the operation is understood and authorized (for example, `apt-get -y`). Avoid unnecessary package installation or automatic upgrades.

## 7. Git and Remote-Operation Policy

**Default profile: Conservative.**

- Inspect changes with `git status` and review the diff before handoff.
- Do not create commits, push branches, pull/rebase, or synchronize Dolt data unless the user or active repository workflow clearly authorizes that operation.
- Do not overwrite, discard, or revert user changes unrelated to the task.
- If synchronization is requested but blocked, report the exact command and error; do not claim it succeeded.

A repository may explicitly opt into a different maintainer workflow. Even then, any current instruction prohibiting a commit, push, or sync takes precedence.

## 8. Session Completion and Handoff

When finishing an implementation task:

1. Review the final diff and working-tree status.
2. Run the relevant quality gates if code changed.
3. Update the related Beads issue if Beads is configured and the workflow authorizes the update.
4. Do not commit, push, or synchronize without authorization.
5. Hand off a concise but complete summary covering:
   - What was implemented.
   - Which files changed.
   - Which build, tests, and checks were run, with actual results.
   - Any incomplete work, blockers, or risks.
   - Suggested next commands when an authorized step remains for the user.

**Completion standard:** report the verified state of the work, not an assumed percentage or an unverified promise of completion.
