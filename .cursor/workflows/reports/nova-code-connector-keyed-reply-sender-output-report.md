# Nova-code run report — Connector-keyed reply sender (outbound delivery refactor)

**Workflow:** nova-code  
**Request:** Outbound delivery refactor — connector-keyed reply sender (engine and Bootstrap use connector id for reply sender registration and resolution).

---

## 1. Workflow executed

| Step | Status |
|------|--------|
| discovery | **Pass** |
| schema_gate | **Pass** |
| drift_gate | **Pass** |
| plan_change | **Pass** |
| branch_removal_rename | **Skip** |
| pre_change_lock | **Pass** |
| implement | **Pass** |
| update_tests | **Pass** |
| update_specs | **Pass** |
| update_readme | **Pass** |
| post_schema | **Pass** |
| traceability | **Pass** |
| run_tests | **Pass** |
| build_check | **Pass** |
| reconcile | **Pass** |
| mk | **Pass** |
| docs_gate | **Pass** |
| output | **Pass** |

All steps in `run_order` were executed. **branch_removal_rename** skipped (removal_or_rename false). Workflow complete.

---

## 2. Per-step outcome

- **discovery:** Pass. Core/connectors registries and engine/Bootstrap scope loaded; primary assets and scope from context.
- **schema_gate:** Pass. All impacted specs valid against JSON Schema.
- **drift_gate:** Pass. Index and registry paths, refs, and traceability validated.
- **plan_change:** Pass. Change context written; removal_or_rename false.
- **branch_removal_rename:** Skip. No removal or rename requested.
- **pre_change_lock:** Pass. Re-validated impacted specs before implement.
- **implement:** Pass. VinekeepersEngine: connector-keyed `replySendersByConnectorId` map, `setReplySender(connectorId, sender)`, backward-compat `setReplySender(ReplySender)` → "discord"; reply resolution by event source prefix; no "discord" literal in engine. Bootstrap: `setReplySender("discord", router)` for Discord outbound.
- **update_tests:** Pass. VinekeepersEngineTest: 3 new tests (connector-keyed sender, fallback by source prefix, backward-compat single-arg setReplySender).
- **update_specs:** Pass. core-registry.yml, connectors-registry.yml updated for engine/Bootstrap reply-sender behavior and assets.
- **update_readme:** Pass. README and mkdoc (engine, discord, architecture, change-logs) updated.
- **post_schema:** Pass. Post-change schema validation passed.
- **traceability:** Pass. Impacted requirements traced; traceability complete.
- **run_tests:** Pass. Tests run: 448, Passed: 448, Failed: 0.
- **build_check:** Pass. `mvn compile` succeeded.
- **reconcile:** Pass. Specs and code reconciled; no dangling refs.
- **mk:** Pass. Docs dir synced; architecture.md updated.
- **docs_gate:** Pass. `npm run validate-docs` passed; docs-dir and mkdocs navigation validated.
- **output:** Pass. Final report produced.

---

## 3. Workflow validation

| Check | Result |
|-------|--------|
| Schema Gate | **Pass** |
| Drift Gate | **Pass** |
| Pre-change lock | **Pass** |
| Post-change schema | **Pass** |
| Tests | **Pass** (run: 448, passed: 448, failed: 0) |
| Static analysis | **Pass** (build_check: mvn compile) |
| Reconcile | **OK** |
| Mk | **Pass** (docs-dir sync; architecture.md updated) |
| Docs gate | **Pass** (validate-docs OK) |
| No unresolved spec drift or blocked tests | **Yes** |

---

## 4. Detail sections

### Summary of change

Refactored outbound delivery to use **connector-keyed reply senders** in the engine and Bootstrap:

- **VinekeepersEngine:** Holds a `Map<String, ReplySender> replySendersByConnectorId`; `setReplySender(String connectorId, ReplySender sender)` registers by connector id; overload `setReplySender(ReplySender)` remains for backward compatibility and registers under `"discord"`. Reply delivery resolves sender by event source prefix (connector id)—no hardcoded `"discord"` in engine logic.
- **Bootstrap:** Registers the Discord outbound path with `engine.setReplySender("discord", outboundDeliveryRouter)` (and passes router to CursorCloudRunMonitor where needed).
- **Fallback:** When no sender is registered for the event’s source prefix, engine uses no sender (no silent fallback to a default); behavior aligns with connector-scoped delivery.

### Changed files

**Modified**

- `src/main/java/com/vinekeepers/core/VinekeepersEngine.java` — connector-keyed reply sender map, `setReplySender(connectorId, sender)` and single-arg overload, resolution by source prefix
- `src/main/java/com/vinekeepers/core/Bootstrap.java` — `setReplySender("discord", outboundDeliveryRouter)` (and monitor wiring)
- `src/test/java/com/vinekeepers/core/VinekeepersEngineTest.java` — 3 new tests for connector-keyed sender, fallback by source prefix, backward-compat single-arg setter
- `specs/core-registry.yml` — ASSET-ENGINE, ASSET-BOOTSTRAP roles and traceability
- `specs/connectors-registry.yml` — connector-keyed reply sender behavior and traceability
- `README.md` — engine and Bootstrap reply-sender description
- `mkdoc/` — engine, discord, architecture, change-logs

### Specs updated

- **specs/specs.yml** — Not modified (index unchanged).
- **specs/core-registry.yml** — ASSET-ENGINE, ASSET-BOOTSTRAP role text and requirements/traceability for connector-keyed reply sender.
- **specs/connectors-registry.yml** — Reply sender registration by connector id; traceability and validation refs.

### Schema validation results

- **specs/specs.yml:** Pass (index valid).
- **specs/core-registry.yml:** Pass (req-registry schema).
- **specs/connectors-registry.yml:** Pass (req-registry schema).

### Drift Gate result

**Pass.** Index and registry paths, refs, and traceability validated; no spec drift issues.

### Test results

**Pass.**  
- Tests run: 448  
- Passed: 448  
- Failed: 0  
- Skipped: (as reported by test run)

New tests in VinekeepersEngineTest cover: connector-keyed `setReplySender(connectorId, sender)`, resolution by source prefix, and backward-compat single-arg `setReplySender(sender)` registering under `"discord"`.

### Static analysis

**Pass.** `mvn compile` completed successfully; no compilation errors.

### Reconcile results

**OK.** Specs and code reconciled; no dangling references or traceability mismatches.

### Mk results

**Pass.** Docs dir synced from specs and handoff. architecture.md updated for connector-keyed reply sender and engine/Bootstrap behavior.

### README changes

README updated to describe engine and Bootstrap reply-sender behavior: connector-keyed registration and resolution, `setReplySender(connectorId, sender)`, and Bootstrap’s `setReplySender("discord", router)` for Discord outbound.

### Issues raised

None. No spec drift, blocked tests, or unmet requirements. All gates and steps passed.

---

## Commit recommendation

- **Branch:** Use a feature branch for this refactor (e.g. `feature/connector-keyed-reply-sender` or per your workflow).
- **Scope:** All changes are cohesive (engine + Bootstrap + tests + specs + README + mkdoc). Safe to commit as a single logical change.
- **Message suggestion:**  
  `refactor(engine): connector-keyed reply sender for outbound delivery`  
  `- Engine: replySendersByConnectorId map, setReplySender(connectorId, sender), resolve by source prefix`  
  `- Bootstrap: setReplySender("discord", outboundDeliveryRouter)`  
  `- Tests: 3 new VinekeepersEngineTest cases; specs and docs updated`
