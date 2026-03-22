package com.vinekeepers.workflow.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.connectors.openai.OpenAiChatClient;
import com.vinekeepers.events.Event;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.profile.WorkProfileRegistry;
import com.vinekeepers.state.planning.AssumptionEntry;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy;
import com.vinekeepers.workflow.planning.PlanningQuestionRankingPolicy.RankedClarification;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.PlanningRole;
import com.vinekeepers.workflow.planning.PlanningRolePassRunner.RolePassResult;
import com.vinekeepers.workflow.planreview.PlanningArtifactTexts;
import com.vinekeepers.workflow.planreview.PlanningPacketDepthEvaluator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One planning-room cycle: exploration seed, architect/auditor/scribe LLM passes (when configured),
 * deterministic draft expansion, synthesis LLM, pre-critique fill, depth check with inner bot retries,
 * question ranking with safe defaults, and spread keys for YAML branching.
 */
public final class ExecutePlanningRoomCycleAction implements com.vinekeepers.workflow.WorkflowAction {

    private static final int MAX_BOT_INNER_ROUNDS = 3;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OpenAiChatClient openAiChatClient;
    private final FeaturePlanStateStore planStateStore;
    private final WorkProfileRegistry workProfileRegistry;

    public ExecutePlanningRoomCycleAction(
            OpenAiChatClient openAiChatClient,
            FeaturePlanStateStore planStateStore,
            WorkProfileRegistry workProfileRegistry) {
        this.openAiChatClient = openAiChatClient;
        this.planStateStore = planStateStore;
        this.workProfileRegistry = workProfileRegistry;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = baseSpread();
        if (planStateStore == null || workProfileRegistry == null) {
            spread.put("planningRoomCycleError", "MISSING_DEPS");
            finishProgressFingerprint(state, spread);
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningRoomCycleError", "NO_CONTEXT");
            finishProgressFingerprint(state, spread);
            return spread;
        }
        FeaturePlanState plan = planStateStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningRoomCycleError", "NO_PLAN");
            finishProgressFingerprint(state, spread);
            return spread;
        }
        String profileId = plan.getProfileId();
        WorkProfileDefinition profile = profileId != null && !profileId.isBlank()
                ? workProfileRegistry.get(profileId).orElse(null)
                : null;
        if (profile == null || profile.findSection("request_exploration", "analysis").isEmpty()) {
            spread.put("planningRoomCycleError", "PROFILE_NOT_V2");
            finishProgressFingerprint(state, spread);
            return spread;
        }

        int cycleIteration = parseInt(getString(state, "planningRoomCycleIteration"), 0);
        cycleIteration++;
        spread.put("planningRoomCycleIteration", String.valueOf(cycleIteration));

        spread.put("planningPhase", "REQUEST_EXPANSION");
        List<String> aggregatedFollowUps = new ArrayList<>();
        Map<String, Object> work = new LinkedHashMap<>();
        if (state != null) {
            work.putAll(state);
        }
        Object expansionObj = new RunRequestExpansionLlmAction(openAiChatClient, planStateStore, workProfileRegistry)
                .run(event, work, bind);
        if (expansionObj instanceof Map<?, ?> expMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> exp = (Map<String, Object>) expMap;
            mergeSpreadIntoWorkAndOuter(exp, work, spread);
        }
        mergeExpansionFollowUpsFromWork(work, aggregatedFollowUps);

        new BuildRequestExplorationAction(planStateStore, workProfileRegistry).run(event, work, bind);
        String depthReason = "";
        boolean depthOk = false;
        String lastRoleRoundSummary = "";
        String lastSynthLlmLine = "";

        for (int inner = 0; inner < MAX_BOT_INNER_ROUNDS; inner++) {
            spread.put("planningPhase", "DRAFTING");
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            List<String> roleTags = new ArrayList<>();
            runRole(PlanningRole.ARCHITECT, plan, profile, event, work, spread, aggregatedFollowUps, roleTags);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            runRole(PlanningRole.AUDITOR, plan, profile, event, work, spread, aggregatedFollowUps, roleTags);
            plan = planStateStore.getByContextId(contextId).orElse(plan);
            runRole(PlanningRole.SCRIBE, plan, profile, event, work, spread, aggregatedFollowUps, roleTags);
            lastRoleRoundSummary = summarizeRoleRound(roleTags);

            new ExpandPlanningDraftsAction(planStateStore, workProfileRegistry).run(event, work, bind);

            Object synthObj =
                    new RunLlmPlanningSynthesisAction(openAiChatClient, planStateStore, workProfileRegistry)
                            .run(event, work, bind);
            @SuppressWarnings("unchecked")
            Map<String, Object> synthSpread =
                    synthObj instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            mergeSpreadIntoWorkAndOuter(synthSpread, work, spread);
            mergeSynthFollowUps(spread, aggregatedFollowUps);
            lastSynthLlmLine = pickSynthLlmLine(synthSpread, lastSynthLlmLine);

            new SynthesizePreCritiqueArtifactsAction(planStateStore, workProfileRegistry).run(event, work, bind);

            plan = planStateStore.getByContextId(contextId).orElse(plan);
            PlanningPacketDepthEvaluator.DepthResult dr = PlanningPacketDepthEvaluator.evaluate(plan);
            depthOk = dr.ok();
            depthReason = dr.reason() != null ? dr.reason() : "";
            spread.put("planningPacketDepthOk", depthOk ? "true" : "false");
            spread.put("planningPacketDepthReason", depthReason);
            spread.put("planningPacketDepthRetryRecommended", depthOk ? "false" : "true");
            if (depthOk) {
                break;
            }
        }

        spread.put("planningCycleRolePassSummary", lastRoleRoundSummary);
        spread.put("planningCycleSynthesisLlmNote", lastSynthLlmLine);

        plan = planStateStore.getByContextId(contextId).orElse(plan);
        RankedClarification ranked = PlanningQuestionRankingPolicy.rank(plan, aggregatedFollowUps, 3);
        for (String assumption : ranked.assumptionsToRecord()) {
            plan = appendAssumption(plan, assumption);
        }
        planStateStore.update(plan);

        spread.put("planningQuestionsAskedThisRound", ranked.userInputRequired() ? "1" : "0");
        spread.put("planningBlockingQuestionCount", String.valueOf(ranked.blockingQuestionCount()));
        spread.put("planningUserInputRequired", ranked.userInputRequired() ? "true" : "false");
        spread.put("planningClarificationChoicesJson", ranked.choicesJson());
        spread.put("planningClarificationMetaJson", ranked.metaJson());
        spread.put("planningClarificationUseStructuredChoices", ranked.useStructuredChoices() ? "true" : "false");
        spread.put("planningClarificationQuestionText", ranked.questionText() != null ? ranked.questionText() : "");
        spread.put(
                "planningClarificationOrchestratorPrompt",
                ranked.orchestratorPrompt() != null ? ranked.orchestratorPrompt() : "");
        boolean readyToPost = depthOk && !ranked.userInputRequired();
        applyClarificationStuck(state, spread, ranked);

        boolean clarificationStuck = "true".equalsIgnoreCase(getString(spread, "planningClarificationStuck"));
        String stuckHint = getString(spread, "planningClarificationStuckHint");
        spread.put(
                "planningOrchestratorRoundSummary",
                buildOrchestratorSummary(
                        plan,
                        depthOk,
                        depthReason,
                        ranked,
                        cycleIteration,
                        readyToPost,
                        clarificationStuck,
                        stuckHint != null ? stuckHint : ""));
        spread.put(
                "planningRevisionNeeded",
                (!depthOk || ranked.userInputRequired()) ? "true" : "false");

        spread.put("planningReadyToPostPacket", readyToPost ? "true" : "false");
        spread.put(
                "planningPhase",
                ranked.userInputRequired()
                        ? "WAITING_FOR_CLARIFICATION"
                        : (readyToPost ? "READY_FOR_APPROVAL" : "REVISING"));
        spread.put("planningAssumptionsUsed", String.valueOf(ranked.assumptionsToRecord().size()));

        if (!readyToPost && !ranked.userInputRequired() && cycleIteration >= 4) {
            spread.put("planningRoomCycleError", "DEPTH_FAIL_AFTER_RETRIES: " + depthReason);
        }
        String rolePassErr =
                spread.get("planningRolePassLastError") != null
                        ? spread.get("planningRolePassLastError").toString()
                        : "";
        String llmErr = firstNonBlank(getString(spread, "planningLlmError"), "");
        spread.put(
                "planningCycleUserVisibleFailure",
                buildUserVisibleFailure(
                        spread.get("planningRoomCycleError") != null
                                ? spread.get("planningRoomCycleError").toString()
                                : "",
                        llmErr));

        spread.put(
                "planningCycleProgressSummary",
                buildCycleProgressSummary(
                        cycleIteration,
                        depthOk,
                        depthReason,
                        ranked,
                        spread.get("planningRoomCycleError") != null
                                ? spread.get("planningRoomCycleError").toString()
                                : "",
                        rolePassErr,
                        lastRoleRoundSummary,
                        lastSynthLlmLine,
                        llmErr,
                        getString(spread, "planningExpansionFallbackUsed"),
                        getString(spread, "planningLlmSkipReason")));

        finishProgressFingerprint(state, spread);
        return spread;
    }

    private static void mergeSpreadIntoWorkAndOuter(
            Map<String, Object> from, Map<String, Object> work, Map<String, Object> spread) {
        if (from == null) {
            return;
        }
        for (Map.Entry<String, Object> e : from.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            String k = e.getKey().toString();
            work.put(k, e.getValue());
            spread.put(k, e.getValue());
        }
    }

    private static void finishProgressFingerprint(Map<String, Object> state, Map<String, Object> spread) {
        String summary =
                spread.get("planningCycleProgressSummary") != null
                        ? spread.get("planningCycleProgressSummary").toString()
                        : "";
        String fullForHash = normalizeProgressPostBody("**Planning cycle** — " + summary);
        String fp = sha256Hex(fullForHash);
        spread.put("planningProgressPostFingerprint", fp);
        String last = state != null ? getString(state, "planningLastProgressPostHash") : null;
        boolean worthy = last == null || last.isBlank() || !fp.equals(last.trim());
        spread.put("planningProgressPostWorthy", worthy ? "true" : "false");
    }

    private static String buildUserVisibleFailure(String cycleError, String planningLlmError) {
        StringBuilder sb = new StringBuilder();
        if (cycleError != null && !cycleError.isBlank()) {
            sb.append(truncateOneLine(cycleError, 200));
        }
        if (planningLlmError != null && !planningLlmError.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(truncateOneLine(planningLlmError, 200));
        }
        return sb.toString().trim();
    }

    private static void applyClarificationStuck(
            Map<String, Object> state, Map<String, Object> spread, RankedClarification ranked) {
        String prevQ = normalizeClarificationQuestion(getString(state, "planningPreviousClarificationQuestionText"));
        String currQ = normalizeClarificationQuestion(ranked.questionText());
        boolean sameAsPrev = ranked.userInputRequired() && !prevQ.isEmpty() && currQ.equals(prevQ);
        int prevCount = parseInt(getString(state, "planningClarificationRepeatCount"), 0);
        int newCount = sameAsPrev ? prevCount + 1 : 0;
        spread.put("planningClarificationRepeatCount", String.valueOf(newCount));
        boolean stuck = sameAsPrev && newCount >= 1;
        spread.put("planningClarificationStuck", stuck ? "true" : "false");
        spread.put(
                "planningClarificationStuckHint",
                stuck
                        ? "We already recorded your answer, but the draft still surfaces the same blocking question. "
                                + "Reply with a concrete example or constraint, use **Use recommended default** if shown, "
                                + "or use the coordinator menu to add scope or reframe the request."
                        : "");
    }

    private static String pickSynthLlmLine(Map<String, Object> synthSpread, String previous) {
        if (synthSpread == null) {
            return previous;
        }
        String err = getString(synthSpread, "planningLlmError");
        if (err != null && !err.isBlank()) {
            return truncateOneLine(err, 140);
        }
        String skip = getString(synthSpread, "planningLlmSkipReason");
        if (skip != null && !skip.isBlank() && !"OK".equalsIgnoreCase(skip)) {
            return "Synthesis skipped: " + truncateOneLine(skip, 120);
        }
        return previous;
    }

    private static void mergeExpansionFollowUpsFromWork(Map<String, Object> work, List<String> aggregated) {
        if (work == null || aggregated == null) {
            return;
        }
        Object raw = work.get("planningExpansionFollowUpsJson");
        if (raw == null) {
            return;
        }
        try {
            List<String> qs = JSON.readValue(raw.toString(), new TypeReference<>() {});
            for (String q : qs) {
                if (q != null && !q.isBlank()) {
                    aggregated.add(q.trim());
                }
            }
        } catch (JsonProcessingException ignored) {
            // ignore
        }
    }

    private static void mergeSynthFollowUps(Map<String, Object> synthesisSpread, List<String> aggregated) {
        if (synthesisSpread == null) {
            return;
        }
        Object raw = synthesisSpread.get("planningFollowUpQuestionsJson");
        if (raw == null) {
            return;
        }
        try {
            List<String> qs = JSON.readValue(raw.toString(), new TypeReference<>() {});
            for (String q : qs) {
                if (q != null && !q.isBlank()) {
                    aggregated.add(q.trim());
                }
            }
        } catch (JsonProcessingException ignored) {
            // ignore
        }
    }

    private void runRole(
            PlanningRole role,
            FeaturePlanState plan,
            WorkProfileDefinition profile,
            Event event,
            Map<String, Object> state,
            Map<String, Object> spread,
            List<String> aggregatedFollowUps,
            List<String> roleRoundTags) {
        spread.put("planningPhase", "CRITIQUING");
        RolePassResult r =
                PlanningRolePassRunner.run(
                        role, openAiChatClient, plan, profile, event, state, planStateStore, workProfileRegistry);
        if (!r.followUps().isEmpty()) {
            aggregatedFollowUps.addAll(r.followUps());
        }
        String label = roleUserLabel(role);
        if (r.skipped() && "NO_API_KEY".equals(r.error())) {
            roleRoundTags.add("SKIP_NO_KEY");
            spread.put("planningRolePassLastError", label + ": skipped (no API key)");
        } else if (r.skipped()) {
            String reason = r.error() != null ? r.error() : "skipped";
            roleRoundTags.add("SKIP_OTHER:" + label + ": " + truncateOneLine(reason, 80));
            spread.put("planningRolePassLastError", label + ": skipped (" + truncateOneLine(reason, 100) + ")");
        } else if (r.error() != null && !r.error().isBlank()) {
            spread.put("planningRolePassLastError", role.name() + ": " + r.error());
            roleRoundTags.add("ERR:" + label + ": " + truncateOneLine(r.error(), 100));
        } else {
            roleRoundTags.add("OK:" + label);
        }
    }

    private static String summarizeRoleRound(List<String> tags) {
        String[] labels = {"Architect", "Auditor", "Scribe"};
        if (tags == null || tags.isEmpty()) {
            return "Role passes not run.";
        }
        if (tags.size() == 3 && tags.stream().allMatch("SKIP_NO_KEY"::equals)) {
            return "Architect, Auditor, and Scribe passes skipped (OpenAI API key not configured).";
        }
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            String t = tags.get(i);
            String who = i < labels.length ? labels[i] : "Role";
            if ("SKIP_NO_KEY".equals(t)) {
                parts.add(who + " skipped (no API key)");
            } else if (t != null && t.startsWith("SKIP_OTHER:")) {
                parts.add(t.substring("SKIP_OTHER:".length()).trim());
            } else if (t != null && t.startsWith("ERR:")) {
                parts.add(t.substring(4).trim());
            } else if (t != null && t.startsWith("OK:")) {
                parts.add(who + " ok");
            } else {
                parts.add(t != null ? t : who);
            }
        }
        return String.join("; ", parts);
    }

    private static String roleUserLabel(PlanningRole role) {
        return switch (role) {
            case ARCHITECT -> "Architect";
            case AUDITOR -> "Auditor";
            case SCRIBE -> "Scribe";
        };
    }

    private static FeaturePlanState appendAssumption(FeaturePlanState plan, String text) {
        if (text == null || text.isBlank()) {
            return plan;
        }
        String id = "asm-auto-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return plan.withAppendedAssumption(new AssumptionEntry(id, text.trim(), null));
    }

    /**
     * One short line for thread progress posts after a planning cycle (plain English).
     */
    private static String buildCycleProgressSummary(
            int cycleIteration,
            boolean depthOk,
            String depthReason,
            RankedClarification ranked,
            String cycleError,
            String rolePassError,
            String roleRoundSummary,
            String synthesisNote,
            String planningLlmError,
            String expansionFallbackUsed,
            String planningLlmSkipReason) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Round ").append(cycleIteration).append(":** ");
        sb.append(roleRoundSummary != null ? roleRoundSummary : "").append(' ');
        if (synthesisNote != null && !synthesisNote.isBlank()) {
            sb.append("Synthesis: ").append(truncateOneLine(synthesisNote, 120)).append(' ');
        }
        if ("true".equalsIgnoreCase(expansionFallbackUsed)) {
            sb.append("Exploration expansion used deterministic fallback. ");
        }
        if (rolePassError != null && !rolePassError.isBlank()) {
            sb.append("Pass note: ").append(truncateOneLine(rolePassError, 120)).append(' ');
        }
        if (planningLlmError != null && planningLlmError.startsWith("ERROR:")) {
            sb.append("LLM: ").append(truncateOneLine(planningLlmError, 120)).append(' ');
        } else if (planningLlmSkipReason != null
                && !planningLlmSkipReason.isBlank()
                && !"MISSING_DEPS".equals(planningLlmSkipReason)) {
            sb.append("Synthesis skip: ").append(truncateOneLine(planningLlmSkipReason, 100)).append(' ');
        }
        if (ranked.userInputRequired()) {
            sb.append("I need one answer before I can finish the draft and post the packet.");
        } else if (depthOk) {
            sb.append("Depth check passed.");
        } else {
            sb.append("Draft still being strengthened: ")
                    .append(truncateOneLine(depthReason != null ? depthReason : "details pending", 140));
        }
        if (cycleError != null && !cycleError.isBlank()) {
            sb.append(" Issue: ").append(truncateOneLine(cycleError, 120));
        }
        return sb.toString().trim();
    }

    private static String truncateOneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\r\n", " ").replace("\n", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    /**
     * User-visible planning round summary: understanding, draft status, assumptions, and optional clarification blocks.
     */
    static String buildOrchestratorSummary(
            FeaturePlanState plan,
            boolean depthOk,
            String depthReason,
            RankedClarification ranked,
            int cycleIteration,
            boolean readyToPostPacket,
            boolean clarificationStuck,
            String stuckHint) {
        String req = plan.getInitialRequest() != null ? plan.getInitialRequest().trim() : "";
        String gist = req.length() > 200 ? req.substring(0, 199) + "…" : req;
        String arch = PlanningArtifactTexts.artifactField(plan, "architecture_notes", "impact", "components_impacted");
        StringBuilder sb = new StringBuilder();
        if (clarificationStuck && stuckHint != null && !stuckHint.isBlank()) {
            sb.append("**Heads up:** ").append(stuckHint).append("\n\n");
        }
        sb.append("**Planning round ").append(cycleIteration).append("**\n\n");
        sb.append("**Here's my current understanding:** ");
        if (gist.isBlank()) {
            sb.append("Your feature request from this thread (no separate summary text on file).");
        } else {
            sb.append(gist);
        }
        sb.append(workspaceSummaryLine(plan));

        sb.append("\n\n**Where the draft stands:** ");
        if (ranked.userInputRequired()) {
            sb.append(
                    "The coordinator passes have run on the current draft, but we are **not** posting the planning packet yet until we resolve the clarification below.");
        } else if (readyToPostPacket) {
            sb.append("Depth check passed; the planning packet is ready to post in this thread for review.");
        } else if (depthOk) {
            sb.append("Depth check passed; the next steps will post or refine the packet.");
        } else {
            sb.append("Still strengthening the draft (").append(depthReason).append("). ");
            sb.append("We will retry automatically inside this round or ask you only when something needs a human decision.");
        }
        if (!arch.isBlank()) {
            sb.append("\n\n**I think this will mainly touch:** ")
                    .append(arch.length() > 200 ? arch.substring(0, 199) + "…" : arch);
        }

        if (!ranked.assumptionsToRecord().isEmpty()) {
            sb.append("\n\n**Assumptions recorded this round (auto):**\n");
            for (String a : ranked.assumptionsToRecord()) {
                sb.append("- ").append(a).append("\n");
            }
        }

        if (ranked.userInputRequired()) {
            String q = ranked.questionText() != null ? ranked.questionText().trim() : "";
            sb.append("\n**I need one answer before I can finish the draft:**\n\n");
            if (!q.isBlank()) {
                sb.append(q).append("\n\n");
            }
            sb.append("**Why this matters:** ");
            if (ranked.blockingQuestionCount() > 0) {
                sb.append(
                        "Your answer affects compatibility, security, migrations, or validation — getting it wrong would be expensive to unwind during implementation.\n\n");
            } else {
                sb.append(
                        "This shapes scope and design choices in the packet so implementation matches what you expect.\n\n");
            }
            sb.append(
                    "**Next I'll:** refresh exploration, run the three coordinator passes, check depth, then post the packet or ask again if something is still ambiguous.\n");
            if (ranked.useStructuredChoices()) {
                sb.append(
                        "\nChoose an option below, or pick **Use recommended default** to record our baseline and continue.");
            } else {
                sb.append("\n**Reply in plain text** with your answer. You can give examples or edge cases — no need to match a fixed list.");
            }
        }
        return sb.toString().trim();
    }

    private static String workspaceSummaryLine(FeaturePlanState plan) {
        if (plan == null) {
            return "";
        }
        String path = plan.getRepoLocalPath() != null ? plan.getRepoLocalPath().trim() : "";
        String st = plan.getRepoWorkspaceStatus() != null ? plan.getRepoWorkspaceStatus().trim() : "";
        if (path.isBlank() && st.isBlank()) {
            return "";
        }
        StringBuilder w = new StringBuilder();
        w.append("\n**Workspace:** ");
        if (!st.isBlank()) {
            w.append("status ").append(st);
        }
        if (!path.isBlank()) {
            if (!st.isBlank()) {
                w.append("; ");
            }
            w.append("local path `").append(path.length() > 120 ? path.substring(0, 119) + "…" : path).append("`");
        }
        w.append(".");
        return w.toString();
    }

    static String normalizeClarificationQuestion(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.toLowerCase().replaceAll("\\s+", " ").trim();
        return t.replaceAll("[^a-z0-9?\\s]", "");
    }

    private static String normalizeProgressPostBody(String full) {
        if (full == null) {
            return "";
        }
        return full.replace("\r\n", "\n").trim();
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> baseSpread() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planningRoomCycleError", "");
        m.put("planningPhase", "");
        m.put("planningUserInputRequired", "false");
        m.put("planningClarificationChoicesJson", "[]");
        m.put("planningClarificationMetaJson", "{}");
        m.put("planningOrchestratorRoundSummary", "");
        m.put("planningPacketDepthOk", "false");
        m.put("planningPacketDepthReason", "");
        m.put("planningPacketDepthRetryRecommended", "false");
        m.put("planningReadyToPostPacket", "false");
        m.put("planningRevisionNeeded", "false");
        m.put("planningQuestionsAskedThisRound", "0");
        m.put("planningBlockingQuestionCount", "0");
        m.put("planningAssumptionsUsed", "0");
        m.put("planningRolePassLastError", "");
        m.put("planningClarificationUseStructuredChoices", "false");
        m.put("planningClarificationQuestionText", "");
        m.put("planningClarificationOrchestratorPrompt", "");
        m.put("planningCycleProgressSummary", "");
        m.put("planningProgressPostWorthy", "true");
        m.put("planningProgressPostFingerprint", "");
        m.put("planningClarificationStuck", "false");
        m.put("planningClarificationStuckHint", "");
        m.put("planningClarificationRepeatCount", "0");
        m.put("planningCycleRolePassSummary", "");
        m.put("planningCycleSynthesisLlmNote", "");
        m.put("planningCycleUserVisibleFailure", "");
        m.put("planningLlmError", "");
        m.put("planningLlmSkipReason", "");
        return m;
    }

    private static int parseInt(String s, int dflt) {
        if (s == null || s.isBlank()) {
            return dflt;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null && !b.isBlank() ? b : null);
    }
}
