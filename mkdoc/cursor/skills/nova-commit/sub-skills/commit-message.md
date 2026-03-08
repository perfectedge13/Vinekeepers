# Commit message

## Summary

Builds or validates the commit message for the verification run.

## Key points

- See **.cursor/skills/nova-commit/sub-skills/commit-message.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Nova-commit-message

**Purpose:** Define formatting and rules for commit messages produced by the nova-commit workflow. Used by the commit-push step when composing the message for git commit.

## Format

- **Subject line:** One concise line (recommended ≤72 characters). Use **imperative mood** (e.g. "Add schema validation script", "Fix registry test IDs").
- **Optional body:** If the change needs explanation, add a blank line after the subject then a short body. Keep lines in the body ≤72 characters.
- **No prefix:** Do not include workflow or tool names in the message (e.g. no "nova-commit:", "[nova-commit]", or similar).

## Rules

1. **Summarize the change** — The subject should describe what the commit does, not what was requested (e.g. "Add dummy file for testing" not "User asked for dummy file").
2. **Imperative mood** — Start with a verb: Add, Fix, Update, Remove, Refactor, etc. Not "Added" or "Fixes".
3. **Scope (optional)** — If useful, prefix with a scope in parentheses: e.g. "(specs) Add requirement-tracking rules". Omit if the change spans many areas.
4. **No meta-tags in subject** — Do not add "[skip ci]", "WIP", or workflow identifiers in the message unless the user explicitly requests them.
5. **Body when needed** — Use a body to list multiple distinct changes, reference requirements, or explain non-obvious decisions. Separate subject and body with a blank line.

## Examples

- Add output-format sub-skill for nova-code report structure
- Fix symbols.requires drift check in validate-drift.cjs
- (specs) Add requirement-tracking rules and expand update-specs process
- Update run-tests sub-skill to always print test counts on step 6

## Usage

When the commit-push step runs, compose the message using the change summary from the run (files/specs changed, what was implemented). Follow the format and rules above so messages are consistent and tool-agnostic.
```

</details>


