package com.vinekeepers.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.env.Env;
import com.vinekeepers.events.Event;
import com.vinekeepers.interactions.ResponseIntent;
import com.vinekeepers.state.StateStore;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.DynamicChoiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches GitHub repos for the authenticated user (sort=updated) and returns them as choices.
 * Prepends "Use last repo" when available; appends "Custom repo".
 */
public final class GitHubReposChoiceProvider implements DynamicChoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubReposChoiceProvider.class);
    private static final String GITHUB_API = "https://api.github.com/user/repos";
    private static final String LAST_REPO_KEY_PREFIX = "luna:lastRepo:";

    private final StateStore stateStore;
    private final java.net.http.HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubReposChoiceProvider(StateStore stateStore) {
        this(stateStore, java.net.http.HttpClient.newHttpClient(), new ObjectMapper());
    }

    public GitHubReposChoiceProvider(StateStore stateStore,
                                     java.net.http.HttpClient httpClient,
                                     ObjectMapper objectMapper) {
        this.stateStore = stateStore != null ? stateStore : new StateStore();
        this.httpClient = httpClient != null ? httpClient : java.net.http.HttpClient.newHttpClient();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public List<ResponseIntent.Choice> getChoices(Event event, ConfigurableWorkflowState state) {
        List<ResponseIntent.Choice> choices = new ArrayList<>();
        String authorId = event != null && event.getPayload() != null
                ? getString(event.getPayload(), "authorId") : null;

        if (authorId != null && !authorId.isBlank()) {
            String lastRepo = stateStore.get(LAST_REPO_KEY_PREFIX + authorId, String.class).orElse(null);
            if (lastRepo != null && !lastRepo.isBlank()) {
                choices.add(new ResponseIntent.Choice(lastRepo, "Use last repo (" + lastRepo + ")", null));
            }
        }

        String token = Env.get("GITHUB_TOKEN", "").trim();
        if (token.isBlank()) {
            log.debug("GITHUB_TOKEN not set; skipping GitHub repo list");
        } else {
            try {
                int perPage = parseInt(Env.get("GITHUB_REPOS_PAGE_SIZE", "25"), 25);
                boolean excludeArchived = !"false".equalsIgnoreCase(Env.get("GITHUB_EXCLUDE_ARCHIVED", "true").trim());
                String query = "?sort=updated&per_page=" + perPage + "&type=all";
                URI uri = URI.create(GITHUB_API + query);
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(uri)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .GET()
                        .build();
                java.net.http.HttpResponse<String> response = httpClient.send(request,
                        java.net.http.HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200 && response.body() != null) {
                    JsonNode root = objectMapper.readTree(response.body());
                    if (root.isArray()) {
                        for (JsonNode repo : root) {
                            if (excludeArchived && repo.path("archived").asBoolean(false)) {
                                continue;
                            }
                            String fullName = repo.has("full_name") ? repo.get("full_name").asText() : "";
                            if (fullName.isBlank()) continue;
                            String name = repo.has("name") ? repo.get("name").asText() : fullName;
                            String updated = repo.has("updated_at")
                                    ? repo.get("updated_at").asText("") : "";
                            String description = updated.isEmpty() ? null : "Updated " + updated.substring(0, Math.min(10, updated.length()));
                            choices.add(new ResponseIntent.Choice(fullName, name + " (" + fullName + ")", description));
                        }
                    }
                } else {
                    log.warn("GitHub API returned {} for user repos", response.statusCode());
                }
            } catch (Exception e) {
                log.debug("GitHub repos fetch failed: {}", e.getMessage());
            }
        }

        choices.add(new ResponseIntent.Choice("__custom__", "Custom repo", "Enter URL or owner/repo manually"));
        return choices;
    }

    private static String getString(java.util.Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
