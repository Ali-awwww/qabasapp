# Project Instructions for AI Agents

# MANDATORY LAW & CORE OPERATIONAL CONSTITUTION
> **CRITICAL LAW FOR ALL AI AGENTS & DEVELOPERS:**
> Every assistant (human or AI) starting or working on this codebase **MUST READ AND ADHERE TO `/CORE_OPERATIONAL_PROMPT.md`** before making any code updates.

```
Lying is strictly forbidden within the operational framework of our application, a fundamental principle that underscores our commitment to ensuring that all interactions and data exchanges are grounded in honesty and integrity. This commitment is not merely a guideline but a cornerstone of our operational ethos, fostering an environment where trust and transparency prevail. If you embark on a task but find yourself unable to complete it within a single session, it is essential to request permission to continue working on it until you achieve a full 100% completion. This approach not only promotes thoroughness and diligence but also ensures that no detail is overlooked, thereby enhancing the overall quality of the work produced. 

Should I instruct you to request something that lacks the necessary components for a feature, you are expected to proactively seek out similar features that can fill in the gaps, demonstrating resourcefulness and initiative in your problem-solving process. This proactive stance is vital in navigating the complexities of our application, as it reflects an understanding of the interconnectedness of features and the importance of adaptability in achieving functional outcomes. In the event that you identify a flaw within the application, it is your responsibility to address and rectify it promptly, maintaining the overall quality and functionality of the system. This responsibility not only safeguards the integrity of our application but also contributes to a culture of continuous improvement and accountability.

Furthermore, any dummy or experimental data generated during testing phases will be systematically deleted to preserve the integrity of our data environment. This meticulous attention to data management is crucial in ensuring that our operational landscape remains free from clutter and confusion, allowing for seamless functionality and user experience. It is imperative to adhere to the prohibition against executing commands by substitution; only the designated terminal commands that are available should be utilized to ensure consistency and reliability in our operations. This strict adherence to protocol is essential in maintaining the robustness of our application and ensuring that all users can rely on its performance without encountering unexpected anomalies.

As you navigate through this system on a new screen, or if you find a screen that suits this design while browsing or executing the Cybernetic Glassmorphism + Linear Minimalist (Spatial HUD Tech), remember that each interaction is an opportunity to uphold these principles and contribute to a cohesive, efficient, and innovative operational environment.
```

---

This file provides instructions and context for AI coding agents working on this project.

<!-- BEGIN BEADS INTEGRATION v:1 profile:minimal hash:46cd31e7 -->
## Beads Issue Tracker

This project uses **bd (beads)** for issue tracking. Run `bd prime` to see full workflow context and commands.

### Quick Reference

```bash
bd ready              # Find available work
bd show <id>          # View issue details
bd update <id> --claim  # Claim work
bd close <id>         # Complete work
```

### Rules

- Use `bd` for ALL task tracking — do NOT use TodoWrite, TaskCreate, or markdown TODO lists
- Run `bd prime` for detailed command reference and session close protocol
- Use `bd remember` for persistent knowledge — do NOT use MEMORY.md files

**Architecture in one line:** issues live in a local Dolt DB; sync uses `refs/dolt/data` on your git remote; `.beads/issues.jsonl` is a passive export. See https://github.com/gastownhall/beads/blob/main/docs/core-concepts/sync-concepts.md for details and anti-patterns.

## Agent Context Profiles

The managed Beads block is task-tracking guidance, not permission to override repository, user, or orchestrator instructions.

- **Conservative (default)**: Use `bd` for task tracking. Do not run git commits, git pushes, or Dolt remote sync unless explicitly asked. At handoff, report changed files, validation, and suggested next commands.
- **Minimal**: Keep tool instruction files as pointers to `bd prime`; use the same conservative git policy unless active instructions say otherwise.
- **Team-maintainer**: Only when the repository explicitly opts in, agents may close beads, run quality gates, commit, and push as part of session close. A current "do not commit" or "do not push" instruction still wins.

## Session Completion

This protocol applies when ending a Beads implementation workflow. It is subordinate to explicit user, repository, and orchestrator instructions.

1. **File issues for remaining work** - Create beads for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **Handle git/sync by active profile**:
   ```bash
   # Conservative/minimal/default: report status and proposed commands; wait for approval.
   git status

   # Team-maintainer opt-in only, unless current instructions forbid it:
   git pull --rebase
   bd dolt push
   git push
   git status
   ```
5. **Hand off** - Summarize changes, validation, issue status, and any blocked sync/commit/push step

**Critical rules:**
- Explicit user or orchestrator instructions override this Beads block.
- Do not commit or push without clear authority from the active profile or the current user request.
- If a required sync or push is blocked, stop and report the exact command and error.
<!-- END BEADS INTEGRATION -->


## Build & Test

_Add your build and test commands here_

```bash
# Example:
# npm install
# npm test
```

## Architecture Overview

_Add a brief overview of your project architecture_

## Conventions & Patterns

_Add your project-specific conventions here_
