# Nova-pr-create

**Inputs:** Optional report path (default: `.cursor/workflows/reports/nova-code-output-report.md`). Optional base branch (default: infer from git, e.g. `main`).

**Outputs:** PR URL and one-line summary, or the exact commands for the user to run and the report path.

## Instructions

1. **Current branch** — Run `git branch --show-current` to get the branch to open a PR for. If not in a git repo or command fails, STOP and report (e.g. "Not in a git repo" or "Could not determine current branch").

2. **Ensure pushed** — Check that the branch has an upstream and is pushed (e.g. `git status` or `git rev-parse --abbrev-ref --symbolic-full-name @{u}`). If the branch is not pushed, run `git push -u origin <branch>` (or tell the user to push first if push fails).

3. **Report path** — Use report path default: `.cursor/workflows/reports/nova-code-output-report.md` (relative to project root). If the file does not exist at that path, STOP and report: "No nova-code report found at .cursor/workflows/reports/nova-code-output-report.md; run nova-code first to generate a report."

4. **PR body** — The PR description (body) is the contents of the report file. Use `--body-file <report-path>` with `gh pr create` and `gh pr edit` so the full nova-code output is used without shell escaping issues.

5. **Create or update PR** — Use **GitHub CLI** (`gh`):
   - Check if `gh` is available: run `gh --version` (or equivalent). If not available, skip to step 6.
   - Check if a PR already exists for the current branch: `gh pr list --head <current-branch> --json number,url`. If the list is non-empty, get the PR number.
   - **If no PR exists:** Run `gh pr create --base <base> --head <current-branch> --title "<title>" --body-file <report-path>`. Use for `<base>` the default branch (e.g. `main`; infer from `git remote show origin` or use `main`). Use for `<title>` the last commit subject: `git log -1 --pretty=%s`.
   - **If a PR exists:** Run `gh pr edit <number> --body-file <report-path>` to update the existing PR's body to the nova-code report.
   - Return the PR URL and a one-line summary (e.g. "Created PR #N" or "Updated PR #N body with nova-code report").

6. **If `gh` is not available** — Do not run `gh` commands. Return:
   - A short message that GitHub CLI (`gh`) is required and the report path: `.cursor/workflows/reports/nova-code-output-report.md`.
   - The exact commands the user can run locally, for example:
     - Create: `gh pr create --base main --head $(git branch --show-current) --title "$(git log -1 --pretty=%s)" --body-file .cursor/workflows/reports/nova-code-output-report.md`
     - Update (if PR exists): `gh pr edit $(gh pr list --head $(git branch --show-current) -q '.[0].number') --body-file .cursor/workflows/reports/nova-code-output-report.md`
   - Note that the PR body will be the nova-code report when they run the command.

**Shell:** Use the project's preferred shell (e.g. PowerShell on Windows: `git branch --show-current`; avoid `&&` in PowerShell, use `;` or separate commands).
