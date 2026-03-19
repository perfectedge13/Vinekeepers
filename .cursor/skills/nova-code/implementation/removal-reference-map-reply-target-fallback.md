# Reference map: Reply Target Resolver completion (fallback removal)

**Removals:** (1) `fallbackReplyTarget(Event)` and its single call in VinekeepersEngine; (2) test `whenNoResolverRegisteredFallbackUsesPayloadForTarget` (replace with fail-closed tests).

**Scope:** spec index, registry specs, README, docs (mkdoc). Code and test file locations for apply step.

---

## 1. Code references (apply removal)

| Location | What | Action |
|----------|------|--------|
| `src/main/java/com/vinekeepers/core/VinekeepersEngine.java` | Line 183: `target = fallbackReplyTarget(event);` | Remove call; when `target == null` after resolver, `return` (do not call fallback). |
| `src/main/java/com/vinekeepers/core/VinekeepersEngine.java` | Lines 264–285 (approx.): method `private static ReplyTarget fallbackReplyTarget(Event event)` | Delete method entirely. |

---

## 2. Test references (remove/replace)

| Location | What | Action |
|----------|------|--------|
| `src/test/java/com/vinekeepers/core/VinekeepersEngineTest.java` | Lines 546–572: test `whenNoResolverRegisteredFallbackUsesPayloadForTarget` | Remove test. Add fail-closed tests: e.g. when no resolver registered for source prefix → reply not delivered; when resolver returns empty → reply not delivered. |

---

## 3. Spec index (specs/specs.yml)

| Item | Reference | Action |
|------|-----------|--------|
| primary_assets | `src/main/java/com/vinekeepers/core/VinekeepersEngine.java` | No change (asset path unchanged). |
| change_triggers, interfaces | N/A | No references to fallback or test name. |

---

## 4. Registry specs

### specs/core-registry.yml

| Item | Location | Current text / ref | Action |
|------|----------|-------------------|--------|
| ASSET-ENGINE role | assets[].role (line ~79) | "obtains ReplyTarget via connector-owned resolver by connector id" | Optional: add "fail closed when no resolver or resolver returns empty" for clarity. |
| REQ-CORE-003 statement | requirements[].statement (line ~213) | "resolves ReplyTarget from event" | Update to: ReplyTarget obtained only via ReplyTargetResolver by connector id; when no resolver or resolver returns empty, engine does not deliver reply (fail closed). |
| REQ-CORE-003 acceptance criterion | requirements[].acceptance.criteria (line ~232) | "Engine uses ReplyTargetResolver registered by connector id when present to obtain ReplyTarget; otherwise fallback for backward compatibility" | Replace with: Engine obtains ReplyTarget only via ReplyTargetResolver by connector id; when no resolver registered for source prefix or resolver returns empty, does not deliver reply (fail closed). |
| validation.tests UNIT-ENGINE | REQ-CORE-003 validation (lines ~240–244) | title: VinekeepersEngineTest, testClass: com.vinekeepers.core.VinekeepersEngineTest | No change (class unchanged; test methods updated in code). |

### specs/connectors-registry.yml

| Item | Reference | Action |
|------|-----------|--------|
| ASSET-*, REQ-* | No mention of fallbackReplyTarget or whenNoResolverRegistered | No change. |

### specs/workflow-registry.yml

| Item | Location | Current text | Action |
|------|----------|--------------|--------|
| ASSET-ENGINE role (traceability) | line ~100 | "resolve ReplyTarget from event" | Update to: obtain ReplyTarget via ReplyTargetResolver by connector id; fail closed when no resolver or empty. |

### specs/reasoner-registry.yml

| Item | Location | Current text | Action |
|------|----------|--------------|--------|
| ASSET-ENGINE role | line ~69 | "resolve ReplyTarget from event" | Update to: obtain ReplyTarget via resolver by connector id; fail closed when no resolver or empty. |

---

## 5. README

| Location | Current text | Action |
|----------|--------------|--------|
| README.md (package table, ~line 63) | "obtains ReplyTarget via resolver by source prefix, fallback target from event" | Replace with: obtains ReplyTarget via resolver by source prefix; does not deliver when no resolver or resolver returns empty (fail closed). |

---

## 6. Docs (mkdoc)

| File | Location | Current text | Action |
|------|----------|--------------|--------|
| mkdoc/features/domain/core/engine/contracts.md | Line 13 (VinekeepersEngine bullet) | "when no resolver is registered uses a fallback target from the event" | Replace with: when no resolver is registered or resolver returns empty, does not deliver reply (fail closed). |
| mkdoc/features/domain/core/engine.md | Line 9 (summary) | "when no resolver is registered uses a fallback target from the event" | Same as above. |
| mkdoc/features/domain/core/engine.md | Line 15 (ASSET-ENGINE table row) | "obtain ReplyTarget via ReplyTargetResolver by connector id (fallback target from event)" | Replace with: obtain ReplyTarget via ReplyTargetResolver by connector id; fail closed when no resolver or empty. |
| mkdoc/features/domain/core/engine/change-log.md | Line 7 | "when no resolver is registered the engine uses a fallback target from the event" | Replace with: when no resolver is registered or resolver returns empty, engine does not deliver reply (fail closed). |
| mkdoc/features/domain/core/engine/how-it-works.md | Line 13 | "when no resolver is registered it uses a fallback target from the event" | Replace with: when no resolver is registered or resolver returns empty, does not deliver reply (fail closed). |
| mkdoc/features/domain/connectors/discord/change-log.md | Line 7 | "when no resolver is registered it uses a fallback target from the event" | Same wording: fail closed when no resolver or empty. |
| mkdoc/features/domain/connectors/discord/how-it-works.md | Line 17 | "When no resolver is registered for a source, the engine uses a fallback target from the event" | Same wording: fail closed when no resolver or empty. |
| mkdoc/features/domain/connectors/discord.md | Line 9 (summary) | "engine obtains ReplyTarget via ... when no resolver" / fallback phrasing if present | Align to: obtain ReplyTarget only via resolver by connector id; fail closed when no resolver or empty. |
| mkdoc/architecture.md | Lines 28, 61 | "fallback target from event when no resolver" | Replace with: fail closed when no resolver or resolver returns empty. |

---

## 7. Context only (no spec/doc update required)

- `.cursor/skills/nova-code/implementation/plan-change-context.md` — describes the removal; used for context.
- `.cursor/workflows/reports/*.md` — historical reports; optional to update.

---

## Summary

- **Code:** 1 call site + 1 method in `VinekeepersEngine.java`.
- **Test:** 1 test method in `VinekeepersEngineTest.java` (remove; add fail-closed tests).
- **Spec index:** No change to paths.
- **Registries:** core (REQ-CORE-003 statement + one criterion), workflow and reasoner (ASSET-ENGINE role wording).
- **README:** 1 phrase in package table.
- **MkDoc:** 9 spots (contracts.md, engine.md summary + table, engine/change-log.md, engine/how-it-works.md, discord/change-log.md, discord/how-it-works.md, discord.md, architecture.md ×2).
