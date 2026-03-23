# Planning coordinator: Discord ingress noise, LLM churn, and misleading progress

## Problem summary

After async engine dispatch, operators still see:

1. **Duplicate Discord ingress** — Luna, Gadget, and Arrietty each publish the same `messageId` to the bus (logs show three `publisher.accept` paths for one user message). Engine dedupe prevents double workflow execution but wastes queue work and confuses operators.
2. **High OpenAI volume per user reply** — A single user message (e.g. clarification “both”) triggers long serial runs on `vinekeepers-engine-events-1` with many `complete()` calls (expansion, Architect/Auditor/Scribe, synthesis, depth, multiple inner rounds).
3. **Sparse Discord updates** — Progress posts happen at coarse workflow boundaries; minutes can pass with no visible activity during `execute_planning_room_cycle`.
4. **Visible errors + contradictory copy** — `StructuredLlmArtifactUpsertPass` logs Architect JSON parse failures (`Unexpected close marker '}'`); user-facing summaries still say “Draft looks solid” when a role pass failed, so updates feel like they are “cycling” between failure and success.

## Root causes (technical)

| Area | Cause |
|------|--------|
| Multi-gateway publish | Several bots use **ROUTED** or **OWNED_SPACES** ingress on overlapping channels; each JDA gateway calls the shared `EventBus` for the same Discord message. |
| Dedupe location | `VinekeepersEngine.isDuplicateDiscordPublication` runs **after** `AsyncEngineEventSubscriber` has already enqueued a task per publish. |
| Inner rounds | `PlanningCyclePipeline` runs up to `MAX_BOT_INNER_ROUNDS` (3) per cycle unless selective “just merged clarification” path correctly forces a single inner round. |
| Structured LLM output | Model (e.g. `gpt-5.4-*`) sometimes returns JSON that fails Jackson parse; HTTP layer still reports success. |
| Progress vs. truth | `buildCycleProgressSummary` / readiness flags do not fully gate “solid draft” messaging on successful Architect structured parse. |

## Goals

- At most **one** bus publication per Discord message (or coalesce before the engine queue) for a given `(guild, channel, messageId)`.
- **Fewer redundant LLM calls** per user turn where safe (especially after clarification merge).
- **Clearer user-visible state**: no “all ok” when Architect parse failed; optional retry/repair path.
- **Better perceived responsiveness** during long planning actions (without undoing JDA-thread offload).

## Proposed work items

### 1. Config / routing — single publisher for shared channels (preferred first step)

- Audit `config/bots.yaml` **routing** and **`identities.discord.ingress`** for Luna, Gadget, and Arrietty.
- Adjust so **only the bot that should own intake/spec thread messages** receives **message** ingress on those channels (e.g. narrow Gadget/Luna `discordChannels` or set message ingress to `none` where Arrietty owns the thread).
- Document the rule: “one message ingress owner per channel/surface” in `mkdoc` or README connector notes.

**Acceptance:** For a message in the intake/spec thread, logs show **one** `Discord message ingress` line (or one publish) before dedupe.

### 2. Optional code — pre-queue dedupe (bus or subscriber wrapper)

- Add a small coalescing layer: e.g. `DedupingEventSubscriber` in front of the async wrapper, key = `discord:{guildId}:{channelId}:{messageId}` (and analogous for interactions), TTL aligned with engine dedupe (~45s).
- Alternative: dedupe inside `EventBus.publish` for Discord kinds only (tighter coupling).

**Acceptance:** Duplicate gateway publishes do not enqueue engine work; metrics/logs show skipped coalesced events.

### 3. Planning pipeline — inner rounds and selective path

- Verify `planningJustMergedClarification` / `planningSelectiveRerunActive` state is set correctly after `merge_planning_clarification_choice` so post-merge runs use **one** inner round when intended.
- Consider lowering default inner rounds or gating round 2–3 on `!depthOk` only (product decision).

**Acceptance:** After a successful clarification merge, trace logs show `innerRounds=1` for that cycle; OpenAI call count drops measurably on that path.

### 4. Structured output resilience

- On Architect (and similar) parse failure in `StructuredLlmArtifactUpsertPass` / `PlanningRolePassRunner`: optional **single retry** with a “return valid JSON only” repair prompt, or switch structured passes to a model known to obey JSON (config-driven).
- If OpenAI supports `response_format` / JSON mode for the deployment, enable it for structured artifact passes.

**Acceptance:** Parse failure rate drops; failures surface a single clear user line (“Architect output was invalid JSON; retrying…” / “say continue to retry”).

### 5. User-visible consistency

- When Architect (or any structured role) fails parse, set spread flags so `planningPacketDepthOk`-driven copy does **not** claim the draft is solid; align `buildCycleProgressSummary` and `buildOrchestratorSummary` with `planningRolePassLastError` / a dedicated `planningStructuredPassFailed` flag.

**Acceptance:** Discord never shows “Architect ok” and parse error in the same logical turn without an explicit recovery step; “solid draft” only when passes succeeded or user acknowledged degraded mode.

### 6. Responsiveness (optional)

- Post a short orchestrator line at **start** of `execute_planning_room_cycle` (or after expansion) via existing `post_planning_progress_if_changed` / `post_channel_message` patterns.
- Document `VINEKEEPERS_ENGINE_EVENT_THREADS` tradeoff (ordering vs. parallel sessions).

## Spec / docs touchpoints

- Update impacted registry specs only if behavior or env contracts change (`specs/events-registry.yml`, `specs/workflow-registry.yml`, `specs/connectors-registry.yml` as needed).
- Short `mkdoc` note under connectors or workflow: multi-bot ingress and “one publisher per channel”.

## Validation

- `npm run validate-specs`, `npm run validate-drift`, `npm run validate-docs`
- `mvn test`, `mvn compile`

## Out of scope (for this plan)

- JDA `VOICE_CHANNEL_START_TIME_UPDATE` debug lines (library noise).
- Changing async engine design unless parallel threads are explicitly chosen for production.

## References (code)

- `VinekeepersEngine.isDuplicateDiscordPublication` — dedupe key and TTL.
- `JdaDiscordGateway` — message/interaction `publisher.accept`.
- `PlanningCyclePipeline` — `MAX_BOT_INNER_ROUNDS`, selective merge path.
- `StructuredLlmArtifactUpsertPass` — parse / error propagation.
- `config/bots.yaml` — Arrietty **`arrietty_room_v2`** routing / ingress.
