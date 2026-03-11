package com.vinekeepers.bot;

import com.vinekeepers.events.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message", Map.of("authorId", "42"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenAuthorNotInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("u1"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message", Map.of("authorId", "u2"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsActorId() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("123456789012345678"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "123456789012345678", "author", "alice", "content", "hi"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsActorUsername() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("novawilde13_72571"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("author", "novawilde13_72571", "content", "ping @Luna"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsActorUsernameCaseInsensitive() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("Novawilde13_72571"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("author", "novawilde13_72571", "content", "hi"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenDiscordAuthorsSetAndActorUsernameNotInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed_user"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("author", "other_user", "authorId", "x99", "content", "hi"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void clearRemovesAllRoutings() {
        router.addRouting(new Routing(new RoutingFilter(null, null, null, null, null, null), "bot-x"));
        router.clear();
        Event event = new Event("discord:g:ch", "message", Map.of());
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesMultipleRoutings() {
        router.addRouting(new Routing(new RoutingFilter(Set.of(), Set.of(), null, Set.of(), Set.of(), Set.of()), "bot-1"));
        router.addRouting(new Routing(new RoutingFilter(Set.of(), Set.of(), null, Set.of(), Set.of(), Set.of()), "bot-2"));
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
        router.addRouting(new Routing(filter, "gh-bot"));
        Event event = new Event("github:repo", "pull_request", Map.of("repo", "owner/repo"));
        assertEquals(List.of("gh-bot"), router.route(event));
    }

    @Test
    void routeMatchesDiscordMentionFromTextCaseInsensitively() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message", Map.of("content", "Hello @LuNa, can you help?"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeMatchesDiscordMentionFromMetadataListCaseInsensitively() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message", Map.of("mentions", List.of("LUNA")));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenDiscordMentionIsMissing() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message", Map.of("content", "Hello there"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void messageWithMentionRoutesToBot() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message", Map.of("content", "Hey @Luna, run it"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void interactionWithoutMentionRoutesToBot() {
        RoutingFilter filter = new RoutingFilter(
                Set.of(), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-1", "authorId", "user-1", "customId", "launch", "interactionId", "i1", "token", "t1"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void interactionFromAllowedAuthorRoutesToBot() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("987654321012345678"), Set.of("ch-1"), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch-1", "interaction",
                Map.of("channelId", "ch-1", "authorId", "987654321012345678", "author", "alice", "customId", "run"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void interactionFromOtherUserDoesNotRoute() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed-user-id"), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "interaction",
                Map.of("channelId", "ch-1", "authorId", "other-user-id", "customId", "launch", "interactionId", "i1", "token", "t1"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsNumericId() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("123456789012345678"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "123456789012345678", "author", "someone", "content", "hi"));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeMatchesWhenDiscordAuthorsContainsUsername() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("alice_dev"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "999", "author", "alice_dev", "content", "hi"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenDiscordAuthorsHasStaleUsername() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("old_nick"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "123", "author", "new_nick", "content", "hi"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeDoesNotMatchWhenDiscordAuthorsHasNumericIdButEventAuthorIdDifferent() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("111222333444555666"), Set.of(), null, null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "999888777666555444", "author", "someone", "content", "hi"));
        assertTrue(router.route(event).isEmpty());
    }

    @Test
    void routeMatchesWhenMentionAndAuthorInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed_user"), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "1", "author", "allowed_user", "content", "Hey @Luna run it", "mentions", List.of("luna")));
        assertEquals(List.of("luna"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenMentionPresentButAuthorNotInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("allowed_user"), Set.of(), null, "luna", Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "luna"));
        Event event = new Event("discord:g:ch", "message",
                Map.of("authorId", "99", "author", "other_user", "content", "Hey @Luna run it", "mentions", List.of("luna")));
        assertTrue(router.route(event).isEmpty());
    }
}
