package com.vinekeepers.workflow.actions;

import com.vinekeepers.events.Event;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InitializeFeatureRoomStateActionTest {

    private FeatureRoomStateStore featureRoomStateStore;

    @BeforeEach
    void setUp() {
        featureRoomStateStore = new FeatureRoomStateStore();
    }

    @Test
    void runReturnsErrorWhenFeatureRoomStateStoreNull() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(null);
        Object result = action.run(null, Map.of(), Map.of());
        assertEquals("FeatureRoomStateStore not available.", result);
    }

    @Test
    void runReturnsErrorWhenFeatureRoomParticipantsMissing() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Object result = action.run(null, Map.of(), Map.of("contextId", "ctx-1", "channelId", "ch-1"));
        assertEquals("Missing or invalid featureRoomParticipants (must be a list).", result);
    }

    @Test
    void runReturnsErrorWhenFeatureRoomParticipantsNotList() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Object result = action.run(null, Map.of("featureRoomParticipants", "not-a-list"), Map.of("contextId", "ctx-1", "channelId", "ch-1"));
        assertEquals("Missing or invalid featureRoomParticipants (must be a list).", result);
    }

    @Test
    void runReturnsErrorWhenFeatureRoomParticipantsSizeNotFour() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        List<Map<String, Object>> twoOnly = List.of(validEntry(PlanningRole.ORCHESTRATOR, "arrietty", "i1"),
                validEntry(PlanningRole.ARCHITECT, "architect", "i2"));
        Object result = action.run(null, Map.of("featureRoomParticipants", twoOnly, "contextId", "ctx-1", "channelId", "ch-1"), Map.of());
        assertEquals("Missing or invalid featureRoomParticipants (expected size 4, got 2).", result);
    }

    @Test
    void runReturnsErrorWhenEntryNotMap() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        List<Object> bad = new ArrayList<>(validFourParticipants());
        bad.set(0, "not-a-map");
        Object result = action.run(null, Map.of("featureRoomParticipants", bad, "contextId", "ctx-1", "channelId", "ch-1"), Map.of());
        assertEquals("Missing or invalid featureRoomParticipants (each entry must be a map).", result);
    }

    @Test
    void runReturnsErrorWhenEntryMissingRequiredKey() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        List<Map<String, Object>> four = new ArrayList<>(validFourParticipants());
        four.get(0).remove("role");
        Object result = action.run(null, Map.of("featureRoomParticipants", four, "contextId", "ctx-1", "channelId", "ch-1"), Map.of());
        assertEquals("Missing or invalid featureRoomParticipants (missing key: role).", result);
    }

    @Test
    void runReturnsErrorWhenRoleInvalid() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        List<Map<String, Object>> four = new ArrayList<>(validFourParticipants());
        four.get(0).put("role", "INVALID_ROLE");
        Object result = action.run(null, Map.of("featureRoomParticipants", four, "contextId", "ctx-1", "channelId", "ch-1"), Map.of());
        assertEquals("Missing or invalid featureRoomParticipants (invalid role: INVALID_ROLE).", result);
    }

    @Test
    void runReturnsErrorWhenConfiguredBotIdBlank() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        List<Map<String, Object>> four = new ArrayList<>(validFourParticipants());
        four.get(0).put("configuredBotId", "");
        Object result = action.run(null, Map.of("featureRoomParticipants", four, "contextId", "ctx-1", "channelId", "ch-1"), Map.of());
        assertEquals("Missing or invalid featureRoomParticipants (configuredBotId and runtimeBotInstanceId must be non-blank).", result);
    }

    @Test
    void runReturnsErrorWhenContextIdMissing() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Object result = action.run(null,
                Map.of("featureRoomParticipants", validFourParticipants(), "channelId", "ch-1"),
                Map.of());
        assertEquals("Missing contextId for initialize_feature_room_state.", result);
    }

    @Test
    void runReturnsErrorWhenRoomChannelIdMissing() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Object result = action.run(null,
                Map.of("featureRoomParticipants", validFourParticipants(), "contextId", "ctx-1"),
                Map.of());
        assertEquals("Missing roomChannelId/channelId for initialize_feature_room_state.", result);
    }

    @Test
    void runBuildsFeatureRoomStateAndPutsInStore() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("featureRoomParticipants", validFourParticipants());
        state.put("contextId", "ctx-init");
        state.put("channelId", "room-ch-123");
        state.put("deliveryChannelId", "thread-456");
        state.put("project", "owner/repo");
        state.put("codeChange", "Add auth");
        state.put("featureId", "feat-1");
        state.put("featureSlug", "add-auth");
        state.put("createdBy", "user-1");

        Object result = action.run(null, state, Map.of());
        assertEquals("OK", result);

        FeatureRoomState roomState = featureRoomStateStore.getByContextId("ctx-init").orElseThrow();
        assertEquals("ctx-init", roomState.getContextId());
        assertEquals("room-ch-123", roomState.getRoomChannelId());
        assertEquals("thread-456", roomState.getIntakeThreadId());
        assertEquals("owner/repo", roomState.getRepo());
        assertEquals("Add auth", roomState.getInitialRequest());
        assertEquals("feat-1", roomState.getFeatureId());
        assertEquals("add-auth", roomState.getFeatureSlug());
        assertEquals("user-1", roomState.getCreatedBy());
        assertEquals(4, roomState.getParticipants().size());
        assertEquals(PlanningRole.ORCHESTRATOR, roomState.getParticipants().get(0).getRole());
        assertEquals("arrietty", roomState.getParticipants().get(0).getConfiguredBotId());
    }

    @Test
    void runReadsParticipantsFromBindWhenNotInState() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Map<String, Object> bind = new LinkedHashMap<>();
        bind.put("featureRoomParticipants", validFourParticipants());
        bind.put("contextId", "ctx-bind");
        bind.put("roomChannelId", "ch-bind");

        Object result = action.run(null, Map.of(), bind);
        assertEquals("OK", result);
        assertTrue(featureRoomStateStore.getByContextId("ctx-bind").isPresent());
        assertEquals("ch-bind", featureRoomStateStore.getByContextId("ctx-bind").orElseThrow().getRoomChannelId());
    }

    @Test
    void runWhenFeatureIdMissing_generatesFeatPlusHex() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("featureRoomParticipants", validFourParticipants());
        state.put("contextId", "ctx-gen");
        state.put("channelId", "room-ch");
        state.put("deliveryChannelId", "thread-1");
        state.put("project", "owner/repo");
        state.put("codeChange", "Add auth");
        state.put("featureSlug", "add-auth");
        state.put("createdBy", "user-1");
        // no featureId

        Object result = action.run(null, state, Map.of());
        assertEquals("OK", result);

        FeatureRoomState roomState = featureRoomStateStore.getByContextId("ctx-gen").orElseThrow();
        String featureId = roomState.getFeatureId();
        assertNotNull(featureId);
        assertFalse(featureId.isBlank());
        assertTrue(featureId.startsWith("feat-"), "featureId should start with feat-");
        assertEquals(17, featureId.length(), "feat- + 12 hex = 17 chars");
        assertTrue(featureId.substring(5).matches("[a-f0-9]{12}"), "suffix should be 12 hex chars");
        assertEquals("add-auth", roomState.getFeatureSlug());
    }

    @Test
    void runWhenFeatureSlugMissing_generatesNonBlankSlug() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("featureRoomParticipants", validFourParticipants());
        state.put("contextId", "ctx-slug");
        state.put("channelId", "room-ch");
        state.put("deliveryChannelId", "thread-1");
        state.put("project", "owner/repo");
        state.put("codeChange", "Implement OAuth login flow");
        state.put("featureId", "feat-abc123");
        state.put("createdBy", "user-1");
        // no featureSlug

        Object result = action.run(null, state, Map.of());
        assertEquals("OK", result);

        FeatureRoomState roomState = featureRoomStateStore.getByContextId("ctx-slug").orElseThrow();
        String slug = roomState.getFeatureSlug();
        assertNotNull(slug);
        assertFalse(slug.isBlank());
        assertTrue(slug.length() <= 32);
        assertEquals("feat-abc123", roomState.getFeatureId());
    }

    @Test
    void runWhenFeatureIdAndFeatureSlugSupplied_preservesThem() {
        InitializeFeatureRoomStateAction action = new InitializeFeatureRoomStateAction(featureRoomStateStore);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("featureRoomParticipants", validFourParticipants());
        state.put("contextId", "ctx-preserve");
        state.put("channelId", "room-ch");
        state.put("deliveryChannelId", "thread-1");
        state.put("project", "owner/repo");
        state.put("codeChange", "Add auth");
        state.put("featureId", "feat-custom-id");
        state.put("featureSlug", "my-custom-slug");
        state.put("createdBy", "user-1");

        Object result = action.run(null, state, Map.of());
        assertEquals("OK", result);

        FeatureRoomState roomState = featureRoomStateStore.getByContextId("ctx-preserve").orElseThrow();
        assertEquals("feat-custom-id", roomState.getFeatureId());
        assertEquals("my-custom-slug", roomState.getFeatureSlug());
    }

    private static Map<String, Object> validEntry(PlanningRole role, String configuredBotId, String runtimeBotInstanceId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role.name());
        m.put("configuredBotId", configuredBotId);
        m.put("runtimeBotInstanceId", runtimeBotInstanceId);
        m.put("displayName", configuredBotId);
        m.put("primaryCoordinator", role == PlanningRole.ORCHESTRATOR);
        return m;
    }

    private static List<Map<String, Object>> validFourParticipants() {
        return List.of(
                validEntry(PlanningRole.ORCHESTRATOR, "arrietty", "inst-o"),
                validEntry(PlanningRole.ARCHITECT, "architect", "inst-a"),
                validEntry(PlanningRole.AUDITOR, "auditor", "inst-u"),
                validEntry(PlanningRole.SCRIBE, "scribe", "inst-s"));
    }
}
