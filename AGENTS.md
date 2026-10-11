<!-- nx configuration start-->
<!-- Leave the start & end comments to automatically receive updates. -->

# General Guidelines for working with Nx

- For navigating/exploring the workspace, invoke the `nx-workspace` skill first - it has patterns for querying projects, targets, and dependencies
- When running tasks (for example build, lint, test, e2e, etc.), always prefer running the task through `nx` (i.e. `nx run`, `nx run-many`, `nx affected`) instead of using the underlying tooling directly
- Prefix nx commands with the workspace's package manager (e.g., `pnpm nx build`, `npm exec nx test`) - avoids using globally installed CLI
- You have access to the Nx MCP server and its tools, use them to help the user
- For Nx plugin best practices, check `node_modules/@nx/<plugin>/PLUGIN.md`. Not all plugins have this file - proceed without it if unavailable.
- NEVER guess CLI flags - always check nx_docs or `--help` first when unsure

## Scaffolding & Generators

- For scaffolding tasks (creating apps, libs, project structure, setup), ALWAYS invoke the `nx-generate` skill FIRST before exploring or calling MCP tools

## When to use nx_docs

- USE for: advanced config options, unfamiliar flags, migration guides, plugin configuration, edge cases
- DON'T USE for: basic generator syntax (`nx g @nx/react:app`), standard commands, things you already know
- The `nx-generate` skill handles generator discovery internally - don't call nx_docs just to look up generator syntax

<!-- nx configuration end-->

## Architecture-debt remediation workflow

For findings in `docs\arch-review\findings`, handle each finding separately:

1. Start from clean, up-to-date `develop`. Before editing, create
   `refactor/<library>/<finding-id>` (for example `refactor/base-document-backend/BDO-SEC-01`).
2. Fix that finding only, preserving unrelated work. Add regression coverage and update the
   library documentation and the finding's remediation/status section; retain the review baseline.
3. Run the relevant tests through the library's Nx target. Resolve failures before committing.
4. Commit as `refactor(<library>): <finding-id>` and push the finding branch.
5. Open a PR targeting `develop`, documenting the change, validation and any remaining limitations.
   Merge through normal repository rules; never bypass required checks or approvals.
6. Verify the PR is merged, fast-forward local `develop`, and confirm a clean worktree before
   starting the next finding on its own branch.

One finding means one branch and one PR, even when several findings share files. Do not accumulate
fixes on `develop` or combine findings into one PR. If blocked, report the exact incomplete stage
instead of claiming completion. Apply this workflow across sessions unless the user changes it.
