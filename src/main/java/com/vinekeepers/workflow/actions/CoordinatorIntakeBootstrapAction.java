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
 * fingerprint/version, and marks discovery complete for critique gates — replaces a non-idempotent
 * {@code post_channel_message} + button menu block.
 */
public final class CoordinatorIntakeBootstrapAction implements com.vinekeepers.workflow.WorkflowAction {

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
        spread.put("humanDiscoveryCompleted", "true");
        spread.put("intakeKickoffPostedSkipped", "false");
        spread.put("intakeKickoffPostedError", "");
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
        return spread;
    }

    private static String buildKickoffBody(String project, String codeChange) {
        return "**Discovery kickoff (coordinator)**\n\n\n\nRepo: `"
                + escapeTicks(project)
                + "`\n\n\n\n**Request:** "
                + codeChange
                + "\n\n\n\nI will explore what this request means, expand drafts from repo context, and post the planning "
                + "packet in this thread when it is ready to read. I will only ask questions if something **blocks** a "
                + "solid plan. Reply in plain text anytime to add scope, must-haves, or risks — otherwise I continue "
                + "drafting automatically.";
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
