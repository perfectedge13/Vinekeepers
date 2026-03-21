package com.vinekeepers.config;

import com.vinekeepers.bot.BotDefinition;
import com.vinekeepers.bot.ConnectorIdentity;
import com.vinekeepers.bot.ConversationMode;
import com.vinekeepers.bot.MemoryPolicy;
import com.vinekeepers.bot.ModelProfile;
import com.vinekeepers.bot.Persona;
import com.vinekeepers.bot.RoutingRule;
import com.vinekeepers.bot.RoutingFilter;
import com.vinekeepers.bot.Router;
import com.vinekeepers.bot.ToolPolicy;

import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads bots and routing from YAML config.
 */
public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    @SuppressWarnings("unchecked")
    public BotConfig loadFromPath(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            LoaderOptions opts = new LoaderOptions();
            opts.setTagInspector(tag -> true);
            Yaml yaml = new Yaml(opts);
            Map<String, Object> root = yaml.load(in);
            BotConfig config = new BotConfig();
            if (root != null) {
                config.setBots((List<Map<String, Object>>) root.get("bots"));
                config.setRouting((List<Map<String, Object>>) root.get("routing"));
                config.setWorkflows((Map<String, Object>) root.get("workflows"));
                Object defaultKey = root.get("defaultDiscordTokenEnvKey");
                config.setDefaultDiscordTokenEnvKey(defaultKey != null ? defaultKey.toString().trim() : null);
            }
            return config;
        }
    }

    /**
     * Build a new Router from config routing section.
     */
    public Router buildRouter(BotConfig config) {
        Router router = new Router();
        addRoutings(config, router);
        return router;
    }

    /**
     * Add routing rules from config to an existing Router.
     */
    public void addRoutings(BotConfig config, Router router) {
        List<Map<String, Object>> routing = config.getRouting();
        if (routing == null) return;
        for (Map<String, Object> r : routing) {
            RoutingFilter filter = parseRoutingFilter(r.get("filter") != null ? (Map<String, Object>) r.get("filter") : null);
            String botId = (String) r.get("botId");
            if (botId != null) {
                router.addRouting(new RoutingRule(filter != null ? filter
                        : new RoutingFilter(null, null, null, null, null, null, null), botId));
            }
        }
    }

    /**
     * Build bot definitions from config bots section.
     */
    public List<BotDefinition> buildBots(BotConfig config) {
        List<Map<String, Object>> bots = config.getBots();
        if (bots == null) return List.of();
        List<BotDefinition> result = new ArrayList<>();
        for (Map<String, Object> b : bots) {
            try {
                result.add(parseBot(b));
            } catch (Exception e) {
                log.warn("Skip bot entry: {}", e.getMessage());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private RoutingFilter parseRoutingFilter(Map<String, Object> f) {
        if (f == null) return new RoutingFilter(null, null, null, null, null, null, null);
        return new RoutingFilter(
                toSet((List<String>) f.get("discordAuthors")),
                toSet((List<String>) f.get("discordChannels")),
                toSet((List<String>) f.get("discordChannelsExclude")),
                (String) f.get("discordTrigger"),
                (String) f.get("discordMention"),
                toSet((List<String>) f.get("repos")),
                toSet((List<String>) f.get("prLabels")),
                toSet((List<String>) f.get("prAuthors")));
    }

    private static Set<String> toSet(List<String> list) {
        if (list == null) return Set.of();
        return new HashSet<>(list);
    }

    @SuppressWarnings("unchecked")
    private BotDefinition parseBot(Map<String, Object> b) {
        String id = (String) b.get("id");
        if (id == null) throw new IllegalArgumentException("bot.id required");
        Map<String, Object> personaMap = (Map<String, Object>) b.get("persona");
        String name = personaMap != null ? (String) personaMap.get("name") : "Bot";
        String systemPrompt = personaMap != null ? (String) personaMap.get("systemPrompt") : "";
        Persona persona = new Persona(name != null ? name : "Bot", systemPrompt != null ? systemPrompt : "");

        Map<String, Object> modelMap = (Map<String, Object>) b.get("model");
        String provider = modelMap != null ? (String) modelMap.get("provider") : "stub";
        String modelId = modelMap != null ? (String) modelMap.get("modelId") : "stub";
        ModelProfile modelProfile = new ModelProfile(provider, modelId);

        ToolPolicy toolPolicy = parseToolPolicy((Map<String, Object>) b.get("toolPolicy"));
        int maxContext = 4096;
        if (b.get("memory") instanceof Map<?, ?> m) {
            Object v = m.get("maxContextTokens");
            if (v instanceof Number n) maxContext = n.intValue();
        }
        MemoryPolicy memoryPolicy = new MemoryPolicy(maxContext);

        String workflowType = "stub";
        Map<String, Object> workflowParams = null;
        if (b.get("workflow") instanceof Map<?, ?> w) {
            if (w.get("type") instanceof String t) workflowType = t;
            if (w.get("params") instanceof Map<?, ?> p) workflowParams = (Map<String, Object>) p;
        }
        ConversationMode conversationMode = ConversationMode.fromValue((String) b.get("conversationMode"));
        String sessionKeyStrategy = (String) b.get("sessionKeyStrategy");
        Map<String, ConnectorIdentity> connectorIdentities = buildConnectorIdentities(b);

        return new BotDefinition(id, persona, modelProfile, toolPolicy, memoryPolicy,
                workflowType, workflowParams, conversationMode, sessionKeyStrategy, connectorIdentities);
    }

    /**
     * Dual-read: identities.discord (tokenEnvKey, handlesOwnedSpaces) and legacy top-level
     * discordTokenEnvKey / handlesOwnedSpaces. New shape preferred; new wins if both present.
     * Malformed identity config is warned and ignored (treated as absent or default).
     */
    @SuppressWarnings("unchecked")
    private Map<String, ConnectorIdentity> buildConnectorIdentities(Map<String, Object> b) {
        String tokenEnvKey = null;
        boolean handlesOwnedSpaces = false;
        Map<String, Object> discordIngress = null;
        String botId = b.get("id") != null ? b.get("id").toString() : "?";

        // Legacy top-level (deprecated but still accepted)
        Object legacyToken = b.get("discordTokenEnvKey");
        if (legacyToken instanceof String s && !s.isBlank()) tokenEnvKey = s.trim();
        if (Boolean.TRUE.equals(b.get("handlesOwnedSpaces"))) handlesOwnedSpaces = true;

        // New shape: identities.discord (preferred; overwrites legacy when valid)
        Object identitiesObj = b.get("identities");
        if (identitiesObj != null && !(identitiesObj instanceof Map)) {
            log.warn("Bot config 'identities' is not a map; ignoring. Bot id: {}", botId);
        } else if (identitiesObj instanceof Map<?, ?> identitiesMap) {
            Object discordObj = ((Map<String, Object>) identitiesMap).get("discord");
            if (discordObj != null && !(discordObj instanceof Map)) {
                log.warn("Bot config 'identities.discord' is not a map; ignoring. Bot id: {}", botId);
            } else if (discordObj instanceof Map<?, ?> discordMap) {
                Map<String, Object> discord = (Map<String, Object>) discordMap;
                Object t = discord.get("tokenEnvKey");
                if (t != null) {
                    if (t instanceof String s && !s.isBlank()) {
                        tokenEnvKey = s.trim();
                    } else {
                        log.warn("Bot config 'identities.discord.tokenEnvKey' is not a non-blank string; ignoring. Bot id: {}", botId);
                    }
                }
                Object h = discord.get("handlesOwnedSpaces");
                if (h != null) {
                    if (h instanceof Boolean) {
                        handlesOwnedSpaces = Boolean.TRUE.equals(h);
                    } else if (h instanceof String s && ("true".equalsIgnoreCase(s.trim()) || "false".equalsIgnoreCase(s.trim()))) {
                        handlesOwnedSpaces = Boolean.parseBoolean(s.trim());
                    } else {
                        log.warn("Bot config 'identities.discord.handlesOwnedSpaces' is not a boolean or true/false string; ignoring, using false. Bot id: {}", botId);
                    }
                }
                Object ingressObj = discord.get("ingress");
                if (ingressObj != null && !(ingressObj instanceof Map)) {
                    log.warn("Bot config 'identities.discord.ingress' is not a map; ignoring. Bot id: {}", botId);
                } else if (ingressObj instanceof Map<?, ?> ingressMap) {
                    Map<String, Object> ing = new HashMap<>();
                    Object msg = ingressMap.get("messages");
                    Object inter = ingressMap.get("interactions");
                    if (msg != null) {
                        ing.put("messages", msg.toString().trim());
                    }
                    if (inter != null) {
                        ing.put("interactions", inter.toString().trim());
                    }
                    if (!ing.isEmpty()) {
                        discordIngress = ing;
                    }
                }
            }
        }

        if (tokenEnvKey == null && !handlesOwnedSpaces) {
            return Map.of();
        }
        Map<String, Object> attrs = new HashMap<>();
        if (tokenEnvKey != null) attrs.put("tokenEnvKey", tokenEnvKey);
        attrs.put("handlesOwnedSpaces", handlesOwnedSpaces);
        if (discordIngress != null) {
            attrs.put("ingress", Map.copyOf(discordIngress));
        }
        return Map.of("discord", new ConnectorIdentity(attrs));
    }

    @SuppressWarnings("unchecked")
    private ToolPolicy parseToolPolicy(Map<String, Object> p) {
        if (p == null) return ToolPolicy.allowAll();
        return new ToolPolicy(toSet((List<String>) p.get("allowed")), toSet((List<String>) p.get("denied")));
    }
}
