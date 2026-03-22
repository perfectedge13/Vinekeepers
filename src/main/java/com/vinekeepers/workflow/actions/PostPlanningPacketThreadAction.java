package com.vinekeepers.workflow.actions;

import com.vinekeepers.connectors.OutboundDeliveryRouter;
import com.vinekeepers.connectors.ReplySender;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeaturePlanState;
import com.vinekeepers.state.planning.FeaturePlanStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.workflow.planreview.PlanningThreadPacketFormatter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Posts the full planning packet to the intake/spec thread in one or more Discord messages.
 */
public final class PostPlanningPacketThreadAction implements com.vinekeepers.workflow.WorkflowAction {

    private final ReplySender replySender;
    private final FeaturePlanStateStore planStore;
    private final OutboundDeliveryRouter outboundDeliveryRouter;

    public PostPlanningPacketThreadAction(ReplySender replySender, FeaturePlanStateStore planStore) {
        this.replySender = replySender;
        this.planStore = planStore;
        this.outboundDeliveryRouter = replySender instanceof OutboundDeliveryRouter r ? r : null;
    }

    @Override
    public Object run(Event event, Map<String, Object> state, Map<String, Object> bind) {
        Map<String, Object> spread = new LinkedHashMap<>();
        spread.put("planningPacketPosted", "false");
        spread.put("planningPacketChunkCount", "0");
        spread.put("planningPacketPostError", "");
        spread.put("planningPacketPostedVersion", "0");
        spread.put("planningPacketSkippedDuplicate", "false");
        spread.put("planningPacketSkipReason", "");
        if (replySender == null || planStore == null) {
            spread.put("planningPacketPostError", "Reply sender or plan store not available.");
            return spread;
        }
        if (outboundDeliveryRouter == null) {
            spread.put("planningPacketPostError", "OutboundDeliveryRouter required for role posts.");
            return spread;
        }
        String channelId = firstNonBlank(getString(bind, "channelId"), getString(state, "channelId"));
        String deliveryChannelId = firstNonBlank(getString(bind, "deliveryChannelId"), getString(state, "deliveryChannelId"));
        if (channelId == null || channelId.isBlank()) {
            spread.put("planningPacketPostError", "Missing channelId.");
            return spread;
        }
        String sendTarget = firstNonBlank(deliveryChannelId, channelId);
        if (CreateThreadAction.THREAD_CREATE_FAILED.equals(sendTarget)) {
            sendTarget = channelId;
        }

        String contextId = firstNonBlank(getString(bind, "contextId"), getString(state, "contextId"));
        if (contextId == null || contextId.isBlank()) {
            spread.put("planningPacketPostError", "Missing contextId.");
            return spread;
        }
        FeaturePlanState plan = planStore.getByContextId(contextId).orElse(null);
        if (plan == null) {
            spread.put("planningPacketPostError", "No plan for context.");
            return spread;
        }
        if (state != null && "false".equals(String.valueOf(state.get("planningPacketDepthOk")))) {
            spread.put("planningPacketPostError", "Depth gate not satisfied; packet post skipped.");
            return spread;
        }

        String request = firstNonBlank(plan.getInitialRequest(), getString(state, "codeChange"));
        String repo = firstNonBlank(plan.getRepoRef(), getString(state, "project"));
        String full = PlanningThreadPacketFormatter.buildFullPacketBody(plan, request, repo);
        String fingerprint = sha256Hex(normalizePacketForFingerprint(full));
        boolean forceRepost = truthy(getString(bind, "forceRepost"));
        String lastFp = getString(state, "planningPacketLastPostedFingerprint");
        if (!forceRepost && fingerprint.equals(lastFp)) {
            spread.put("planningPacketPostedVersion", String.valueOf(parsePostedVersion(state)));
            spread.put("planningPacketSkippedDuplicate", "true");
            spread.put("planningPacketLastPostedFingerprint", lastFp);
            spread.put(
                    "planningPacketSkipReason",
                    "Planning packet body is unchanged since the last post in this thread, so no duplicate was sent. "
                            + "Assumptions and exploration may still have been updated—see the latest summary above.");
            return spread;
        }
        List<String> chunks = PlanningThreadPacketFormatter.splitForDiscord(full);
        if (chunks.isEmpty()) {
            spread.put("planningPacketPostError", "Empty planning packet.");
            return spread;
        }
        for (String chunk : chunks) {
            Optional<String> err =
                    outboundDeliveryRouter.sendAsRoleExplicit(sendTarget, null, chunk, PlanningRole.ORCHESTRATOR);
            if (err.isPresent()) {
                spread.put("planningPacketPostError", err.get());
                return spread;
            }
        }
        spread.put("planningPacketPosted", "true");
        spread.put("planningPacketChunkCount", String.valueOf(chunks.size()));
        spread.put("planningPacketSkippedDuplicate", "false");
        spread.put("planningPacketSkipReason", "");
        spread.put("planningPacketLastPostedFingerprint", fingerprint);
        int prev = parsePostedVersion(state);
        spread.put("planningPacketPostedVersion", String.valueOf(prev + 1));
        return spread;
    }

    private static int parsePostedVersion(Map<String, Object> state) {
        if (state == null || state.get("planningPacketPostedVersion") == null) {
            return 0;
        }
        try {
            return Integer.parseInt(state.get("planningPacketPostedVersion").toString().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String normalizePacketForFingerprint(String full) {
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

    private static boolean truthy(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim().toLowerCase();
        return "true".equals(t) || "1".equals(t) || "yes".equals(t);
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
