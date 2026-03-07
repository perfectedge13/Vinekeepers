# Removal/rename reference map: CursorCloudGatheringRunner & cursor_cloud_gathering

**Plan:** Removal. Delete class `CursorCloudGatheringRunner` and remove `cursor_cloud_gathering` from WorkflowRunnerFactory.

**Scanned:** spec index (specs.yml), registry specs (core-registry.yml, connectors-registry.yml), README, mkdoc, source, tests, .cursor/out.

---

## 1. Spec index (specs/specs.yml)

| Location | Reference | Action |
|----------|-----------|--------|
| `primary_assets` | (none — CursorCloudGatheringRunner not listed) | No change |
| `change_triggers` | (none) | No change |
| `interfaces.cli.command` | (none) | No change |

**Result:** Index does not reference the asset path; no update needed for removal.

---

## 2. Registry specs (assets, validation, traceability)

### specs/core-registry.yml

| Location | Line(s) | Reference | Action |
|----------|---------|-----------|--------|
| **assets** | 275–279 | `ASSET-CURSOR-CLOUD-GATHERING-RUNNER`, path `src/main/java/com/vinekeepers/workflow/CursorCloudGatheringRunner.java`, role, requires REQ-WORKFLOW-001, REQ-LUNA-001 | Remove entire asset entry |
| **assets** | 280–283 | ASSET-WORKFLOW-RUNNER-FACTORY `role`: "… (stub, cursor_cloud_gathering, configured)" | Update role to drop `cursor_cloud_gathering` |
| **requirements** | 731–751 | REQ-WORKFLOW-001: statement, acceptance criteria mention cursor_cloud_gathering; traceability.assets includes ASSET-CURSOR-CLOUD-GATHERING-RUNNER | Update statement/criteria to stub, configured only; remove ASSET-CURSOR-CLOUD-GATHERING-RUNNER from traceability.assets |
| **validation.tests** | 754–758 | UNIT-WORKFLOW-RUNNER-FACTORY intent "stub, cursor_cloud_gathering, configured" | Update intent to "stub, configured" |
| **requirements** | 791–815 | REQ-LUNA-001: statement "workflow type cursor_cloud_gathering", "CursorCloudGatheringRunner"; acceptance "cursor_cloud_gathering", "CursorCloudGatheringRunner runs it"; traceability.assets includes ASSET-CURSOR-CLOUD-GATHERING-RUNNER | Update to configured + workflowRef/luna_cursor; remove ASSET-CURSOR-CLOUD-GATHERING-RUNNER from traceability.assets |
| **features** | 887–891 | FEAT-WORKFLOW asset_ids includes ASSET-CURSOR-CLOUD-GATHERING-RUNNER | Remove ASSET-CURSOR-CLOUD-GATHERING-RUNNER from asset_ids |
| **features** | 898–903 | FEAT-LUNA asset_ids includes ASSET-CURSOR-CLOUD-GATHERING-RUNNER | Remove ASSET-CURSOR-CLOUD-GATHERING-RUNNER from asset_ids |

### specs/connectors-registry.yml

| Location | Reference | Action |
|----------|-----------|--------|
| (grep) | No matches for CursorCloudGatheringRunner or cursor_cloud_gathering | No change |

---

## 3. README

| File | Line(s) | Reference | Action |
|------|---------|-----------|--------|
| README.md | 13 | workflow.type examples "stub", "cursor_cloud_gathering", "configured"; Luna "workflow.type: cursor_cloud_gathering" | Change examples to stub, configured; Luna to configured + workflowRef |
| README.md | 37 | Project layout table: `CursorCloudGatheringRunner` in com.vinekeepers.workflow | Remove CursorCloudGatheringRunner from table |

---

## 4. Mkdoc

| File | Line(s) | Reference | Action |
|------|---------|-----------|--------|
| mkdoc/features/domain/core/config/change-log.md | 7 | "stub, cursor_cloud_gathering, or configured" | Change to "stub or configured" |
| mkdoc/architecture.md | 23 | "stub, cursor_cloud_gathering, or configured" | Change to "stub or configured" |
| mkdoc/features/domain/core/config.md | 9 | workflow.type "stub, cursor_cloud_gathering, configured" | Change to "stub, configured" |
| mkdoc/features/domain/core/workflow/change-log.md | 19 | "CursorCloudGatheringRunner; … (stub, cursor_cloud_gathering)" | Remove CursorCloudGatheringRunner; change to stub, configured |
| mkdoc/features/domain/core/workflow/tests.md | 5, 11 | "cursor_cloud_gathering" in factory description and UNIT-WORKFLOW-RUNNER-FACTORY row | Update to stub, configured only |
| mkdoc/features/domain/core/workflow/contracts.md | 15 | Types "stub", "cursor_cloud_gathering", "configured" | Change to "stub", "configured" |
| mkdoc/features/domain/core/workflow/how-it-works.md | 5 | "stub, cursor_cloud_gathering, or configured" | Change to "stub or configured" |
| mkdoc/features/domain/core/workflow.md | 9, 16, 18 | workflow types; assets table ASSET-WORKFLOW-RUNNER-FACTORY (cursor_cloud_gathering); ASSET-CURSOR-CLOUD-GATHERING-RUNNER row | Remove cursor_cloud_gathering from types and factory; delete ASSET-CURSOR-CLOUD-GATHERING-RUNNER row |
| mkdoc/features/domain/core/luna/change-log.md | 7 | "CursorCloudGatheringRunner", "workflow.type cursor_cloud_gathering" | Update to configured + workflowRef / ConfigurableWorkflowRunner |
| mkdoc/features/domain/core/luna.md | 17 | ASSET-CURSOR-CLOUD-GATHERING-RUNNER row | Remove row or update to configured runner |

---

## 5. Source

| File | Line(s) | Reference | Action |
|------|---------|-----------|--------|
| src/main/java/com/vinekeepers/workflow/CursorCloudGatheringRunner.java | (file) | Class CursorCloudGatheringRunner implements WorkflowRunner | **Delete file** |
| src/main/java/com/vinekeepers/workflow/WorkflowRunnerFactory.java | 23, 33 | Javadoc "cursor_cloud_gathering"; case "cursor_cloud_gathering" -> new CursorCloudGatheringRunner(...) | Remove case branch and CursorCloudGatheringRunner import/use; update Javadoc |
| config/bots.yaml | 10 | Luna workflow type: cursor_cloud_gathering | Change to configured + workflowRef (e.g. luna_cursor) |

---

## 6. Tests

| File | Line(s) | Reference | Action |
|------|---------|-----------|--------|
| src/test/java/com/vinekeepers/workflow/WorkflowRunnerFactoryTest.java | 35–45 | createCursorCloudGatheringReturnsCursorCloudGatheringRunner(); create("cursor_cloud_gathering", …); assert CursorCloudGatheringRunner | Remove test method(s); keep stub and configured cases |
| src/test/java/com/vinekeepers/config/ConfigLoaderTest.java | 85, 96 | YAML type: cursor_cloud_gathering; assertEquals "cursor_cloud_gathering" | Change to configured + workflowRef; update assertion |
| src/test/java/com/vinekeepers/core/VinekeepersEngineTest.java | 18, 86, 116 | import CursorCloudGatheringRunner; registerRunner("luna", new CursorCloudGatheringRunner(...)) | Replace with ConfigurableWorkflowRunner (or factory-created runner with configured + luna_cursor) |

---

## 7. .cursor/out (context only; not spec authority)

| File | Reference | Note |
|------|-----------|------|
| .cursor/out/plan_change_change_context.md | Removal plan, ASSET-CURSOR-CLOUD-GATHERING-RUNNER, cursor_cloud_gathering | Plan artifact; can be updated after removal |
| .cursor/out/nova-code-run-report.md | CursorCloudGatheringRunner, cursor_cloud_gathering in narrative | Report artifact; optional update |

---

## Summary

| Category | Files / locations | Actions |
|----------|-------------------|--------|
| **Spec index** | specs.yml | No change (asset not in primary_assets). |
| **Registry** | core-registry.yml | Remove asset; update ASSET-WORKFLOW-RUNNER-FACTORY role; update REQ-WORKFLOW-001, REQ-LUNA-001 statement/criteria/traceability; update UNIT-WORKFLOW-RUNNER-FACTORY intent; remove from FEAT-WORKFLOW and FEAT-LUNA asset_ids. |
| **README** | README.md | Update workflow.type examples and project layout table. |
| **Mkdoc** | 10 files | Remove or reword cursor_cloud_gathering and CursorCloudGatheringRunner; drop ASSET-CURSOR-CLOUD-GATHERING-RUNNER from tables. |
| **Source** | CursorCloudGatheringRunner.java, WorkflowRunnerFactory.java, config/bots.yaml | Delete runner class; remove factory case; set Luna to configured. |
| **Tests** | WorkflowRunnerFactoryTest, ConfigLoaderTest, VinekeepersEngineTest | Remove cursor_cloud_gathering tests; use configured in config test; use ConfigurableWorkflowRunner for Luna in engine test. |

Reference map complete for **reference_map** step. Next step per removal-rename skill: apply removal/rename, then re-run gates and verify.
