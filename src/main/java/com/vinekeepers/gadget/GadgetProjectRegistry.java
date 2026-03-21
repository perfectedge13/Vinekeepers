package com.vinekeepers.gadget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads deployable Gadget projects from YAML (immutable list, optional lookup by id).
 */
public final class GadgetProjectRegistry {

    private static final Logger log = LoggerFactory.getLogger(GadgetProjectRegistry.class);

    private final List<GadgetProjectDefinition> projects;
    private final Map<String, GadgetProjectDefinition> byId;

    public GadgetProjectRegistry(List<GadgetProjectDefinition> projects) {
        List<GadgetProjectDefinition> copy = new ArrayList<>();
        if (projects != null) {
            for (GadgetProjectDefinition p : projects) {
                if (p != null && p.isValid()) {
                    copy.add(p);
                }
            }
        }
        this.projects = List.copyOf(copy);
        Map<String, GadgetProjectDefinition> m = new ConcurrentHashMap<>();
        for (GadgetProjectDefinition p : this.projects) {
            m.putIfAbsent(p.getId().toLowerCase(), p);
        }
        this.byId = Collections.unmodifiableMap(m);
    }

    public List<GadgetProjectDefinition> getProjects() {
        return projects;
    }

    public Optional<GadgetProjectDefinition> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(id.trim().toLowerCase()));
    }

    /**
     * Load from path; returns empty registry if file is missing or invalid.
     */
    @SuppressWarnings("unchecked")
    public static GadgetProjectRegistry load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return new GadgetProjectRegistry(List.of());
        }
        try (InputStream in = Files.newInputStream(path)) {
            LoaderOptions opts = new LoaderOptions();
            opts.setTagInspector(tag -> true);
            Yaml yaml = new Yaml(opts);
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return new GadgetProjectRegistry(List.of());
            }
            Object raw = root.get("projects");
            if (!(raw instanceof List<?> list)) {
                return new GadgetProjectRegistry(List.of());
            }
            List<GadgetProjectDefinition> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String id = stringVal(map.get("id"));
                    String label = stringVal(map.get("label"));
                    String playbook = stringVal(map.get("playbook"));
                    String gitRemote = stringVal(map.get("gitRemote"));
                    if (gitRemote.isEmpty()) {
                        gitRemote = stringVal(map.get("repo"));
                    }
                    Map<String, String> extraVars = stringMap(map.get("extraVars"));
                    out.add(new GadgetProjectDefinition(id, label, playbook,
                            gitRemote.isEmpty() ? null : gitRemote, extraVars));
                }
            }
            return new GadgetProjectRegistry(out);
        } catch (Exception e) {
            log.warn("Could not load gadget projects from {}: {}", path, e.getMessage());
            return new GadgetProjectRegistry(List.of());
        }
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                out.put(e.getKey().toString().trim(), e.getValue().toString());
            }
        }
        return out;
    }
}
