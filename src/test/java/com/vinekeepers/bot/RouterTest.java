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
                Set.of("u1"), Set.of(), null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message", Map.of("authorId", "u1"));
        assertEquals(List.of("bot-a"), router.route(event));
    }

    @Test
    void routeDoesNotMatchWhenAuthorNotInFilter() {
        RoutingFilter filter = new RoutingFilter(
                Set.of("u1"), Set.of(), null, Set.of(), Set.of(), Set.of());
        router.addRouting(new Routing(filter, "bot-a"));
        Event event = new Event("discord:g:ch", "message", Map.of("authorId", "u2"));
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
}
