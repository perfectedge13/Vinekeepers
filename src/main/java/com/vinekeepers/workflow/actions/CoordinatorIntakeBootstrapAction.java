package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningIntakeStage;
import com.vinekeepers.state.planning.PlanningRole;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Idempotent coordinator kickoff post for intake threads, updates {@link FeaturePlanState} intake kickoff
 * fingerprint/version, and advances {@link PlanningIntakeStage} to drafting — replaces a non-idempotent
 * {@code post_channel_message} + button menu block.
 */
public final class CoordinatorIntakeBootstrapAction implements com.vinekeepers.workflow.WorkflowAction {

    public static final String KICKOFF_VISIBLE_OUTCOME_KEY = "coordinatorKickoffVisibleOutcome";
    public static final String KICKOFF_VISIBLE_OUTCOME_FALSE = "false";
    public static final String KICKOFF_VISIBLE_OUTCOME_TRUE = "true";

    private final OutboundDeliveryRouter outboundDeliveryRouter;
    private final FeaturePlanStateStore planStore;

    public CoordinatorIntakeBootstrapAction(
            OutboundDeliveryRouter outboundDeliveryRouter, FeaturePlanStateStore planStore) {
        this.outboundDeliveryRouter = outboundDeliveryRouter;
        this.planStore = planStore;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("intakeKickoffPostedSkipped", "false");
        spread.put("intakeKickoffPostedError", "");
        spread.put(KICKOFF_VISIBLE_OUTCOME_KEY, KICKOFF_VISIBLE_OUTCOME_FALSE);
        if (outboundDeliveryRouter == null || planStore == null) {
            spread.put("intakeKickoffPostedError", "Router or plan store not available.");
            return spread;
        }
        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("intakeKickoffPostedError", "Missing contextId.");
            return spread;
        }
        Optional<FeaturePlanState> opt = planStore.getByContextId(contextId);
        if (opt.isEmpty()) {
            spread.put("intakeKickoffPostedError", "No plan for context.");
            return spread;
        }
        FeaturePlanState plan = opt.get();
        String project = firstNonBlank(getString(state, "project"), plan.getRepoRef());
        String codeChange = firstNonBlank(getString(state, "codeChange"), plan.getInitialRequest());
        String channelId = firstNonBlank(getString(state, "channelId"), plan.getRoomChannelId());
        String deliveryChannelId =
                firstNonBlank(getString(state, "deliveryChannelId"), plan.getIntakeThreadId());
        String sendTarget = firstNonBlank(deliveryChannelId, channelId);
        if (CreateThreadAction.THREAD_CREATE_FAILED.equals(sendTarget)) {
            sendTarget = channelId;
        }
        String body =
                buildKickoffBody(project != null ? project : "", codeChange != null ? codeChange : "");
        String fingerprint = sha256Hex(normalizeForFingerprint(body));
        if (fingerprint.equals(plan.getIntakeKickoffPostedFingerprint())
                && plan.getIntakeKickoffPostedVersion() > 0) {
            spread.put("intakeKickoffPostedSkipped", "true");
            spread.put(KICKOFF_VISIBLE_OUTCOME_KEY, KICKOFF_VISIBLE_OUTCOME_TRUE);
            return spread;
        }
        Optional<String> err =
                outboundDeliveryRouter.sendAsRoleExplicit(sendTarget, null, body, PlanningRole.ORCHESTRATOR);
        if (err.isPresent()) {
            spread.put("intakeKickoffPostedError", err.get());
            return spread;
        }
        int nextVer = plan.getIntakeKickoffPostedVersion() + 1;
        planStore.update(
                plan.withIntakeKickoffPosted(nextVer, fingerprint)
                        .withPlanningIntakeStage(PlanningIntakeStage.DRAFTING, null));
        spread.put("canonicalPlanningIntakeStage", PlanningIntakeStage.DRAFTING.name());
        spread.put(KICKOFF_VISIBLE_OUTCOME_KEY, KICKOFF_VISIBLE_OUTCOME_TRUE);
        return spread;
    }

    private static String buildKickoffBody(String project, String codeChange) {
        return "**Discovery kickoff (coordinator)**\n\n\n\nRepo: `"
                + escapeTicks(project)
                + "`\n\n\n\n**Request:** "
                + codeChange
                + "\n\n\n\nI am continuing to draft in this thread from your request and repo context, and I will post "
                + "the planning packet when it is ready. I only pause with a **single plain-text question** if something "
                + "**blocks** a solid plan.";
    }

    private static String escapeTicks(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('`', '\'');
    }

    private static String normalizeForFingerprint(String full) {
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

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
