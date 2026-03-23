package com.vinekeepers.bot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vinekeepers.events.Event;
import com.vinekeepers.state.LifecycleContext;
import com.vinekeepers.state.LifecycleContextStore;
import com.vinekeepers.state.planning.FeatureRoomState;
import com.vinekeepers.state.planning.FeatureRoomStateStore;
import com.vinekeepers.state.planning.PlanningRole;
import com.vinekeepers.state.planning.RoomParticipant;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    private Router router;

    @BeforeEach
    void setUp() {
        router = new Router();
    }

    @Test
    void routeReturnsEmptyWhenNoRoutings() {
        Event event = new Event("discord:guild:channel", "message", Map.of("authorId", "u1"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void addRoutingAndRouteMatchesDiscordByAuthor() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("42"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message", Map.of("authorId", "42"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenAuthorNotInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("u1"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message", Map.of("authorId", "u2"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsActorId() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("123456789012345678"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "123456789012345678", "author", "alice", "content", "hi"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsActorUsername() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("novawilde13_72571"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("author", "novawilde13_72571", "content", "ping @Luna"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsActorUsernameCaseInsensitive() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("Novawilde13_72571"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("author", "novawilde13_72571", "content", "hi"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenDiscordAuthorsSetAndActorUsernameNotInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed_user"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("author", "other_user", "authorId", "x99", "content", "hi"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void clearRemovesAllRoutings() {
        router.addRouting(new RoutingRule(new RoutingFilter(null, null, null, null, null, null), "bot-x"));
        router.clear();
        Event event = new Event("discord:g:ch", "message", Map.of());
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesMultipleRoutings() {
        router.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, Set.of(), Set.of(), Set.of()), "bot-1"));
        router.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, Set.of(), Set.of(), Set.of()), "bot-2"));
        Event event = new Event("discord:g:ch", "message", Map.of());
        List<String> ids = router.route(event);
        assertEquals(2, ids.size());
        assertTrue(ids.contains("bot-1"));
        assertTrue(ids.contains("bot-2"));
    }

    @Test
    void routeMatchesGitHubByRepo() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, Set.of("owner/repo"), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "gh-bot"));
        Event event = new Event("github:repo", "pull_request", Map.of("repo", "owner/repo"));
        assertEquals(List.of("gh-bot"), router.route(event));
    }

    @Test
    void routeMatchesDiscordMentionFromTextCaseInsensitively() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message", Map.of("content", "Hello @LuNa, can you help?"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeMatchesDiscordMentionFromMetadataListCaseInsensitively() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message", Map.of("mentions", List.of("LUNA")));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenDiscordMentionIsMissing() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message", Map.of("content", "Hello there"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void messageWithMentionRoutesToBot() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message", Map.of("content", "Hey @Luna, run it"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void interactionWithoutMentionRoutesToBot() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-1", "authorId", "user-1", "customId", "launch", "interactionId", "i1", "token", "t1"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void interactionFromAllowedAuthorRoutesToBot() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("987654321012345678"), Set.of("ch-1"), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch-1", "interaction",
                Map.of("channelId", "ch-1", "authorId", "987654321012345678", "author", "alice", "customId", "run"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void interactionFromOtherUserDoesNotRoute() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed-user-id"), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-1", "authorId", "other-user-id", "customId", "launch", "interactionId", "i1", "token", "t1"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsNumericId() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("123456789012345678"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "123456789012345678", "author", "someone", "content", "hi"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsUsername() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("alice_dev"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "999", "author", "alice_dev", "content", "hi"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenDiscordAuthorsHasStaleUsername() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("old_nick"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "123", "author", "new_nick", "content", "hi"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeDoesNotMatchWhenDiscordAuthorsHasNumericIdButEventAuthorIdDifferent() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("111222333444555666"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "999888777666555444", "author", "someone", "content", "hi"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesWhenMentionAndAuthorInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed_user"), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "1", "author", "allowed_user", "content", "Hey @Luna run it", "mentions", List.of("luna")));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeMatchesGadgetWhenMentionAndChannelAllowlist() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of("ops-chan"), null, "gadget", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "gadget"));
        Event event = new Event("discord:g:guild", "message",
                Map.of("channelId", "ops-chan", "content", "@Gadget deploy", "mentions", List.of("gadget")));
        assertEquals(List.of("gadget"), router.route(event));
    }

    @Test
    void routeMatchesGadgetInOpsChannelWhenDirectGadgetMentionMetadata() {
        String opsChannelId = "1484800775342391378";
        RoutingFilter luna = new RoutingFilter(
                Set.of("novawilde13_72571"),
                Set.of(),
                Set.of(opsChannelId),
                null,
                "luna",
                Set.of(),
                Set.of(),
                Set.of());
        RoutingFilter gadget = new RoutingFilter(
                Set.of(),
                Set.of(opsChannelId),
                Set.of(),
                null,
                "gadget",
                Set.of(),
                Set.of(),
                Set.of());
        router.addRouting(new RoutingRule(luna, "luna"));
        router.addRouting(new RoutingRule(gadget, "gadget"));
        Event event = new Event("discord:g:guild", "message", Map.of(
                "channelId", opsChannelId,
                "author", "novawilde13_72571",
                "authorId", "111",
                "content", "@Gadget deploy",
                "mentions", List.of("gadget")));
        assertEquals(List.of("gadget"), router.route(event));
    }

    @Test
    void routeMatchesGadgetInOpsThreadWhenParentChannelAllowlisted() {
        String opsChannelId = "1484800775342391378";
        RoutingFilter gadget = new RoutingFilter(
                Set.of(),
                Set.of(opsChannelId),
                Set.of(),
                null,
                "gadget",
                Set.of(),
                Set.of(),
                Set.of());
        router.addRouting(new RoutingRule(gadget, "gadget"));
        Event event = new Event("discord:g:guild", "message", Map.of(
                "channelId", "thread-ops-1",
                "parentChannelId", opsChannelId,
                "threadId", "thread-ops-1",
                "author", "novawilde13_72571",
                "content", "@Gadget deploy",
                "mentions", List.of("gadget")));
        assertEquals(List.of("gadget"), router.route(event));
    }

    @Test
    void routeExcludesLunaInOpsChannelWhenConfiguredExcludeMatches() {
        String opsChannelId = "1484800775342391378";
        RoutingFilter luna = new RoutingFilter(
                Set.of("novawilde13_72571"),
                Set.of(),
                Set.of(opsChannelId),
                null,
                "luna",
                Set.of(),
                Set.of(),
                Set.of());
        RoutingFilter gadget = new RoutingFilter(
                Set.of(),
                Set.of(opsChannelId),
                Set.of(),
                null,
                "gadget",
                Set.of(),
                Set.of(),
                Set.of());
        router.addRouting(new RoutingRule(luna, "luna"));
        router.addRouting(new RoutingRule(gadget, "gadget"));
        Event event = new Event("discord:g:guild", "message", Map.of(
                "channelId", opsChannelId,
                "author", "novawilde13_72571",
                "content", "Hey @Luna help",
                "mentions", List.of("luna")));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeExcludesLunaInOpsThreadWhenParentChannelExcluded() {
        String opsChannelId = "1484800775342391378";
        RoutingFilter luna = new RoutingFilter(
                Set.of("novawilde13_72571"),
                Set.of(),
                Set.of(opsChannelId),
                null,
                "luna",
                Set.of(),
                Set.of(),
                Set.of());
        router.addRouting(new RoutingRule(luna, "luna"));
        Event event = new Event("discord:g:guild", "message", Map.of(
                "channelId", "thread-ops-1",
                "parentChannelId", opsChannelId,
                "threadId", "thread-ops-1",
                "author", "novawilde13_72571",
                "content", "Hey @Luna help",
                "mentions", List.of("luna")));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeDoesNotMatchDiscordMentionFromNonNumericAngleBracketText() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("content", "Hello <@luna> ping"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeDoesNotMatchUserIdMentionFilterFromRolePingInText() {
        String userId = "123456789012345678";
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, userId, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("content", "Mods <@&" + userId + "> please"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesDiscordMentionNumericIdFromAngleBracketText() {
        String userId = "123456789012345678";
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, userId, Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("content", "Hey <@" + userId + "> help"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void routeLogsInfoWithDiagnosticsWhenNoRuleMatches() {
        Assumptions.assumeTrue(
                LoggerFactory.getILoggerFactory() instanceof LoggerContext,
                "Logback LoggerContext required to capture Router logs");
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger routerLogger = lc.getLogger(Router.class.getName());
        Level prior = routerLogger.getLevel();
        routerLogger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        routerLogger.addAppender(listAppender);
        try {
            RoutingFilter filter = new RoutingFilter(
                    Set.of(), Set.of("other-channel"), null, null, Set.of(), Set.of(), Set.of());
            router.addRouting(new RoutingRule(filter, "bot-x"));
            String longText = "x".repeat(200);
            Event event = new Event("discord:g:guild", "message", Map.of(
                    "channelId", "ch-diag",
                    "authorId", "author-42",
                    "author", "alice",
                    "content", longText,
                    "mentions", List.of("someone"),
                    "ingestBotId", "gadget"));
            assertTrue(router.route(event).isEmpty());

            List<ILoggingEvent> infos = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .filter(e -> e.getFormattedMessage().contains("No routing rule matched"))
                    .toList();
            assertTrue(infos.size() >= 1, "Expected no-match INFO log; got: " + listAppender.list);
            String msg = infos.get(0).getFormattedMessage();
            assertTrue(msg.contains("ch-diag"), msg);
            assertTrue(msg.contains("author-42"), msg);
            assertTrue(msg.contains("alice"), msg);
            assertTrue(msg.contains("gadget"), msg);
            assertTrue(msg.contains("someone"), msg);
            assertTrue(msg.contains("…"), msg);
        } finally {
            routerLogger.detachAppender(listAppender);
            listAppender.stop();
            routerLogger.setLevel(prior);
        }
    }

    @Test
    void routeLogsRolePingHintWhenNoRuleMatches() {
        Assumptions.assumeTrue(
                LoggerFactory.getILoggerFactory() instanceof LoggerContext,
                "Logback LoggerContext required to capture Router logs");
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger routerLogger = lc.getLogger(Router.class.getName());
        Level prior = routerLogger.getLevel();
        routerLogger.setLevel(Level.INFO);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        routerLogger.addAppender(listAppender);
        try {
            RoutingFilter filter = new RoutingFilter(
                    Set.of(), Set.of("ops-room"), null, "gadget", Set.of(), Set.of(), Set.of());
            router.addRouting(new RoutingRule(filter, "gadget"));
            Event event = new Event("discord:g:guild", "message", Map.of(
                    "channelId", "ops-room",
                    "authorId", "author-42",
                    "author", "alice",
                    "content", "<@&1484802559884398602>",
                    "ingestBotId", "gadget"));
            assertTrue(router.route(event).isEmpty());

            List<ILoggingEvent> infos = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.INFO)
                    .filter(e -> e.getFormattedMessage().contains("No routing rule matched"))
                    .toList();
            assertTrue(infos.size() >= 1, "Expected no-match INFO log; got: " + listAppender.list);
            String msg = infos.get(0).getFormattedMessage();
            assertTrue(msg.contains("rolePingDetected=true"), msg);
            assertTrue(msg.contains("discord role pings do not satisfy discordMention"), msg);
        } finally {
            routerLogger.detachAppender(listAppender);
            listAppender.stop();
            routerLogger.setLevel(prior);
        }
    }

    @Test
    void routeDoesNotMatchGadgetWhenChannelNotInAllowlist() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of("ops-chan"), null, "gadget", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "gadget"));
        Event event = new Event("discord:g:guild", "message",
                Map.of("channelId", "other-chan", "content", "@Gadget deploy", "mentions", List.of("gadget")));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeDoesNotMatchWhenChannelInDiscordChannelsExclude() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed_user"),
                Set.of(),
                Set.of("exclude-ch"),
                null,
                "luna",
                Set.of(),
                Set.of(),
                Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event interaction = new Event("discord:g:guild", "interaction",
                Map.of("channelId", "exclude-ch", "author", "allowed_user"));
        assertTrue(router.route(interaction).isEmpty());
        Event message = new Event("discord:g:guild", "message",
                Map.of("channelId", "exclude-ch", "author", "allowed_user",
                        "mentions", List.of("luna"), "content", "hi @Luna"));
        assertTrue(router.route(message).isEmpty());
    }

    @Test
    void routeDoesNotMatchWhenMentionPresentButAuthorNotInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed_user"), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new RoutingRule(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "99", "author", "other_user", "content", "Hey @Luna run it", "mentions", List.of("luna")));
        assertTrue(router.route(event).isEmpty());
    }

    // --- LifecycleContextStore + handlesOwnedSpaces (single-owner precedence) ---

    @Test
    void routeWithLifecycleStore_ownedChannel_returnsOnlyOwnerWhenOwnerHasHandlesOwnedSpaces() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "ch-owned", Instant.now(), null, "arrietty", null, null, null);
        store.put(ctx);
        Router r = new Router(store);
        r.setHandlesOwnedSpacesByBotId(Map.of("arrietty", true, "luna", false));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "arrietty"));

        Event event = new Event("discord:g:ch-owned", "message",
                Map.of("channelId", "ch-owned", "content", "hello"));
        assertEquals(List.of("arrietty"), r.route(event));
    }

    @Test
    void routeWithLifecycleStore_ownedChannel_usesFilterBasedWhenOwnerDoesNotHaveHandlesOwnedSpaces() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext("ctx-1", "ch-owned", Instant.now(), null, "other-bot", null, null, null);
        store.put(ctx);
        Router r = new Router(store);
        r.setHandlesOwnedSpacesByBotId(Map.of("other-bot", false, "luna", true));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:ch-owned", "message",
                Map.of("channelId", "ch-owned", "content", "hello"));
        assertEquals(List.of("luna"), r.route(event));
    }

    @Test
    void routeWithLifecycleStore_ownedChannel_logsOwnershipMismatchWarningWhenOwnerDoesNotHaveHandlesOwnedSpaces() {
        Assumptions.assumeTrue(
                LoggerFactory.getILoggerFactory() instanceof LoggerContext,
                "Logback LoggerContext required to capture Router logs");
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger routerLogger = lc.getLogger(Router.class.getName());
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        routerLogger.addAppender(listAppender);
        try {
            LifecycleContextStore store = new LifecycleContextStore();
            LifecycleContext ctx = new LifecycleContext("ctx-1", "ch-owned", Instant.now(), null, "other-bot", null, null, null);
            store.put(ctx);
            Router r = new Router(store);
            r.setHandlesOwnedSpacesByBotId(Map.of("other-bot", false, "luna", true));
            r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));

            Event event = new Event("discord:g:ch-owned", "message",
                    Map.of("channelId", "ch-owned", "content", "hello"));
            r.route(event);

            List<ILoggingEvent> warnings = listAppender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN)
                    .filter(e -> e.getFormattedMessage().contains("does not have handlesOwnedSpaces"))
                    .toList();
            assertTrue(warnings.size() >= 1,
                    "Expected at least one WARN log containing 'does not have handlesOwnedSpaces'; got: " + listAppender.list);
        } finally {
            routerLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }

    @Test
    void routeWithLifecycleStore_noContextForChannel_usesFilterBased() {
        LifecycleContextStore store = new LifecycleContextStore();
        Router r = new Router(store);
        r.setHandlesOwnedSpacesByBotId(Map.of("arrietty", true));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:ch-unknown", "message",
                Map.of("channelId", "ch-unknown", "content", "hi"));
        assertEquals(List.of("luna"), r.route(event));
    }

    @Test
    void routeWithLifecycleStore_eventWithoutChannelId_usesFilterBased() {
        LifecycleContextStore store = new LifecycleContextStore();
        store.put(new LifecycleContext("ctx-1", "ch-1", Instant.now(), null, "arrietty", null, null, null));
        Router r = new Router(store);
        r.setHandlesOwnedSpacesByBotId(Map.of("arrietty", true));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:ch-1", "message", Map.of("content", "hi"));
        assertEquals(List.of("luna"), r.route(event));
    }

    @Test
    void setHandlesOwnedSpacesByBotIdWithNullDoesNotThrow() {
        Router r = new Router(new LifecycleContextStore());
        r.setHandlesOwnedSpacesByBotId(null);
        Event event = new Event("discord:g:ch", "message", Map.of("channelId", "ch", "content", "x"));
        assertTrue(r.route(event).isEmpty());
    }

    @Test
    void routePreservesOrderOfRoutingRules() {
        router.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "first"));
        router.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "second"));
        Event event = new Event("discord:g:ch", "message", Map.of());
        List<String> botIds = router.route(event);
        assertEquals(List.of("first", "second"), botIds);
    }

    @Test
    void routeLifecyclePrecedenceOverFilterWhenOwnerHasHandlesOwnedSpaces() {
        LifecycleContextStore store = new LifecycleContextStore();
        store.put(new LifecycleContext("ctx-1", "ch-owned", Instant.now(), null, "owner-bot", null, null, null));
        Router r = new Router(store);
        r.setHandlesOwnedSpacesByBotId(Map.of("owner-bot", true));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "other"));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "owner-bot"));
        Event event = new Event("discord:g:ch-owned", "message", Map.of("channelId", "ch-owned", "content", "hi"));
        assertEquals(List.of("owner-bot"), r.route(event));
    }

    // --- Feature room: coordinator-only on room channel and on intake thread messages (planning ingress); legacy single-owner unchanged ---

    @Test
    void routeWithFeatureRoomStore_roomChannelId_returnsCoordinatorOnly() {
        FeatureRoomStateStore featureStore = new FeatureRoomStateStore();
        List<RoomParticipant> participants = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", "Arrietty", true));
        FeatureRoomState roomState = new FeatureRoomState(
                "ctx-1", "feat-1", null, "room-ch-1", "thread-intake", null, null, "INTAKE_READY",
                participants, null, Instant.now());
        featureStore.put(roomState);
        Router r = new Router(new LifecycleContextStore(), featureStore);
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:room-ch-1", "message", Map.of("channelId", "room-ch-1", "content", "hi"));
        List<String> botIds = r.route(event);
        assertEquals(List.of("arrietty"), botIds);
    }

    @Test
    void routeWithFeatureRoomStore_intakeThreadId_message_returnsCoordinatorOnly() {
        FeatureRoomStateStore featureStore = new FeatureRoomStateStore();
        List<RoomParticipant> participants = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", "Arrietty", true));
        FeatureRoomState roomState = new FeatureRoomState(
                "ctx-1", "feat-1", null, "room-ch-parent", "thread-456", null, null, "INTAKE_READY",
                participants, null, Instant.now());
        featureStore.put(roomState);
        Router r = new Router(new LifecycleContextStore(), featureStore);

        Event event = new Event("discord:g:room-ch-parent", "message", Map.of("channelId", "thread-456", "content", "reply in thread"));
        List<String> botIds = r.route(event);
        assertEquals(List.of("arrietty"), botIds);
    }

    @Test
    void routeWithFeatureRoomStore_intakeThreadId_interaction_returnsCoordinatorOnly() {
        FeatureRoomStateStore featureStore = new FeatureRoomStateStore();
        List<RoomParticipant> participants = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", "Arrietty", true));
        featureStore.put(new FeatureRoomState(
                "ctx-1", "feat-1", null, "room-ch-parent", "thread-456", null, null, "INTAKE_READY",
                participants, null, Instant.now()));
        Router r = new Router(new LifecycleContextStore(), featureStore);
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:x", "interaction", Map.of(
                "channelId", "thread-456",
                "authorId", "user-1",
                "customId", "approve",
                "interactionId", "i1",
                "token", "t1"));
        assertEquals(List.of("arrietty"), r.route(event));
    }

    @Test
    void routeWithFeatureRoomStore_noFeatureRoomForChannel_usesLegacySingleOwnerWhenApplicable() {
        LifecycleContextStore lifecycleStore = new LifecycleContextStore();
        lifecycleStore.put(new LifecycleContext("ctx-1", "owned-ch", Instant.now(), null, "arrietty", null, null, null));
        FeatureRoomStateStore featureStore = new FeatureRoomStateStore();
        Router r = new Router(lifecycleStore, featureStore);
        r.setHandlesOwnedSpacesByBotId(Map.of("arrietty", true));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "arrietty"));

        Event event = new Event("discord:g:owned-ch", "message", Map.of("channelId", "owned-ch", "content", "hi"));
        assertEquals(List.of("arrietty"), r.route(event));
    }

    @Test
    void routeWithFeatureRoomStore_roomChannelWinsOverLifecycleOwnerOnSameChannel() {
        String sharedCh = "shared-room-ch";
        LifecycleContextStore lifecycleStore = new LifecycleContextStore();
        lifecycleStore.put(new LifecycleContext("ctx-life", sharedCh, Instant.now(), null, "luna", null, null, null));
        FeatureRoomStateStore featureStore = new FeatureRoomStateStore();
        List<RoomParticipant> participants = List.of(
                new RoomParticipant(PlanningRole.ORCHESTRATOR, "arrietty", "i-o", "Arrietty", true));
        featureStore.put(new FeatureRoomState(
                "ctx-feat", "feat-1", null, sharedCh, "thread-intake", null, null, "INTAKE_READY",
                participants, null, Instant.now()));
        Router r = new Router(lifecycleStore, featureStore);
        r.setHandlesOwnedSpacesByBotId(Map.of("luna", true));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:" + sharedCh, "message", Map.of("channelId", sharedCh, "content", "hi"));
        assertEquals(List.of("arrietty"), r.route(event));
    }

    @Test
    void routeWithLifecycleStore_eventOnDeliveryThreadId_resolvesLifecycleOwner() {
        LifecycleContextStore store = new LifecycleContextStore();
        LifecycleContext ctx = new LifecycleContext(
                "ctx-del", "room-parent", Instant.now(), null, "arrietty", null, null, null, null, "thread-delivery");
        store.put(ctx);
        Router r = new Router(store);
        r.setHandlesOwnedSpacesByBotId(Map.of("arrietty", true));
        r.addRouting(new RoutingRule(new RoutingFilter(Set.of(), Set.of(), null, null, Set.of(), Set.of(), Set.of()), "luna"));

        Event event = new Event("discord:g:thread-delivery", "message",
                Map.of("channelId", "thread-delivery", "content", "in thread"));
        assertEquals(List.of("arrietty"), r.route(event));
    }
}
