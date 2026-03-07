# Nova-commit run report

**Workflow:** nova-commit

---

## 1. Gates

| Gate | Result |
|------|--------|
| Schema Gate | **Pass** / **Skipped** — _(specs/specs.yml and registries validated or schemas not present.)_ |
| Drift Gate | **Pass** / **Skipped** — _(Index and registry integrity or skip_when.)_ |

---

## 2. Discovery

- **Index:** specs/specs.yml — _(N registries or "not present.")_
- **Primary assets:** _(paths or "N/A.")_
- **Validation:** _(From **specs/specs.yml** `validation` or **@.cursor/project.yml** `validation_defaults`:_ test command, static_analysis command, shell.)

---

## 3. Test results

| Result | Details |
|--------|--------|
| **Pass** | _(test command from config)_ completed with exit code 0. |

---

## 4. Requirement plausibility

- **Scope:** _(All accepted requirements or "No specs.")_
- **Implemented?** _(All true or N/A.)_
- **Deprecated:** None blocking.

---

## 5. Docs reconcile

No dangling refs. Traceability consistent. README matches. _(Or list changes.)_

---

## 6. Static analysis

| Result | Details |
|--------|--------|
| **Pass** | _(static_analysis command from config)_ completed with exit code 0. |

---

## 7. Commit + Push

| Result | Details |
|--------|--------|
| **Yes** / **No** | _(Commit message and push result, or reason skipped.)_ |

---

## 8. Summary

All phases completed. _(Commit/push executed or skipped with reason.)_
