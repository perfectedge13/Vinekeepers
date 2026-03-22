package com.vinekeepers.devops;

import com.vinekeepers.env.Env;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads deploy targets from YAML ({@code targets} or legacy {@code projects} root key).
 */
public final class DeployTargetRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeployTargetRegistry.class);

    private final List<DeployTarget> targets;
    private final Map<String, DeployTarget> byId;

    public DeployTargetRegistry(List<DeployTarget> targets) {
        List<DeployTarget> copy = new ArrayList<>();
        if (targets != null) {
            for (DeployTarget t : targets) {
                if (t != null && t.isValid()) {
                    copy.add(t);
                }
            }
        }
        this.targets = List.copyOf(copy);
        Map<String, DeployTarget> m = new ConcurrentHashMap<>();
        for (DeployTarget t : this.targets) {
            m.putIfAbsent(t.getId().toLowerCase(), t);
        }
        this.byId = Collections.unmodifiableMap(m);
    }

    public List<DeployTarget> getTargets() {
        return targets;
    }

    public Optional<DeployTarget> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.trim().toLowerCase()));
    }

    /**
     * Config path: {@code DEPLOY_TARGETS_PATH}, legacy {@code GADGET_PROJECTS_PATH}, else
     * {@code config/deploy-targets.yaml} if present, else {@code config/gadget-projects.yaml}.
     */
    public static Path resolveManifestPath() {
        String p = Env.get("DEPLOY_TARGETS_PATH", "").trim();
        if (!p.isBlank()) {
            return Path.of(p);
        }
        String legacy = Env.get("GADGET_PROJECTS_PATH", "").trim();
        if (!legacy.isBlank()) {
            return Path.of(legacy);
        }
        Path primary = Path.of("config", "deploy-targets.yaml");
        if (Files.isRegularFile(primary)) {
            return primary;
        }
        return Path.of("config", "gadget-projects.yaml");
    }

    public static DeployTargetRegistry load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return new DeployTargetRegistry(List.of());
        }
        try (InputStream in = Files.newInputStream(path)) {
            LoaderOptions opts = new LoaderOptions();
            opts.setTagInspector(tag -> true);
            Yaml yaml = new Yaml(opts);
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return new DeployTargetRegistry(List.of());
            }
            Object raw = root.get("targets");
            if (!(raw instanceof List<?>)) {
                raw = root.get("projects");
            }
            if (!(raw instanceof List<?> list)) {
                return new DeployTargetRegistry(List.of());
            }
            List<DeployTarget> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add(parseTarget(map));
                }
            }
            return new DeployTargetRegistry(out);
        } catch (Exception e) {
            log.warn("Could not load deploy targets from {}: {}", path, e.getMessage());
            return new DeployTargetRegistry(List.of());
        }
    }

    @SuppressWarnings("unchecked")
    private static DeployTarget parseTarget(Map<?, ?> map) {
        String id = stringVal(map.get("id"));
        String label = stringVal(map.get("label"));
        String playbook = stringVal(map.get("playbook"));
        String gitRemote = stringVal(map.get("gitRemote"));
        if (gitRemote.isEmpty()) {
            gitRemote = stringVal(map.get("repo"));
        }
        Map<String, String> extraVars = stringMap(map.get("extraVars"));
        DeployTargetCompose compose = parseCompose(map.get("compose"));
        return new DeployTarget(id, label, playbook, gitRemote.isEmpty() ? null : gitRemote, extraVars, compose);
    }

    @SuppressWarnings("unchecked")
    private static DeployTargetCompose parseCompose(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return DeployTargetCompose.NONE;
        }
        String file = stringVal(m.get("file"));
        String wd = stringVal(m.get("workingDirectory"));
        if (wd.isEmpty()) {
            wd = stringVal(m.get("working_dir"));
        }
        List<String> services = new ArrayList<>();
        Object sv = m.get("services");
        if (sv instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    services.add(o.toString().trim());
                }
            }
        }
        ComposeHostExecutor ex = ComposeHostExecutor.fromYaml(m.get("hostOpsExecutor"));
        if (file.isEmpty() && wd.isEmpty() && services.isEmpty()) {
            return DeployTargetCompose.NONE;
        }
        return new DeployTargetCompose(file, wd, services, ex);
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey().toString().trim(), e.getValue().toString());
            }
        }
        return out;
    }
}
