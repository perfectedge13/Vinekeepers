package com.vinekeepers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@link WorkProfileDefinition} instances from YAML (e.g. config/work-profiles.yaml).
 */
public final class WorkProfileLoader {

    private static final Logger log = LoggerFactory.getLogger(WorkProfileLoader.class);

    private WorkProfileLoader() {}

    @SuppressWarnings("unchecked")
    public static WorkProfileRegistry load(Path path) {
        WorkProfileRegistry registry = new WorkProfileRegistry();
        if (path == null || !path.toFile().exists()) {
            log.warn("Work profiles file not found: {}", path);
            return registry;
        }
        try (InputStream in = Files.newInputStream(path)) {
            LoaderOptions opts = new LoaderOptions();
            opts.setTagInspector(tag -> true);
            Yaml yaml = new Yaml(opts);
            Map<String, Object> root = yaml.load(in);
            if (root == null) {
                return registry;
            }
            Object profilesNode = root.get("profiles");
            if (!(profilesNode instanceof Map<?, ?> profilesMap)) {
                log.warn("work-profiles: missing or invalid 'profiles' map");
                return registry;
            }
            for (Map.Entry<?, ?> e : profilesMap.entrySet()) {
                if (e.getValue() instanceof Map<?, ?> raw) {
                    WorkProfileDefinition def = parseProfile((Map<String, Object>) raw);
                    if (def != null) {
                        registry.register(def);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not load work profiles from {}: {}", path, e.getMessage());
        }
        return registry;
    }

    @SuppressWarnings("unchecked")
    private static WorkProfileDefinition parseProfile(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String profileId = stringVal(raw.get("profileId"));
        if (profileId.isBlank()) {
            return null;
        }
        String title = stringVal(raw.get("title"));
        List<ArtifactDefinition> artifacts = new ArrayList<>();
        Object arts = raw.get("artifacts");
        if (arts instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    ArtifactDefinition a = parseArtifact((Map<String, Object>) m);
                    if (a != null) {
                        artifacts.add(a);
                    }
                }
            }
        }
        return new WorkProfileDefinition(profileId, title, artifacts);
    }

    @SuppressWarnings("unchecked")
    private static ArtifactDefinition parseArtifact(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String artifactId = stringVal(raw.get("artifactId"));
        if (artifactId.isBlank()) {
            return null;
        }
        String title = stringVal(raw.get("title"));
        List<String> ownerRoles = stringList(raw.get("ownerRoles"));
        boolean requiredForApproval = booleanVal(raw.get("requiredForApproval"));
        List<SectionDefinition> sections = new ArrayList<>();
        Object secs = raw.get("sections");
        if (secs instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    SectionDefinition s = parseSection((Map<String, Object>) m);
                    if (s != null) {
                        sections.add(s);
                    }
                }
            }
        }
        return new ArtifactDefinition(artifactId, title, ownerRoles, requiredForApproval, sections);
    }

    @SuppressWarnings("unchecked")
    private static SectionDefinition parseSection(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String sectionId = stringVal(raw.get("sectionId"));
        if (sectionId.isBlank()) {
            return null;
        }
        String title = stringVal(raw.get("title"));
        boolean repeatable = booleanVal(raw.get("repeatable"));
        boolean required = booleanVal(raw.get("required"));
        List<FieldDefinition> fields = new ArrayList<>();
        Object f = raw.get("fields");
        if (f instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    FieldDefinition fd = parseField((Map<String, Object>) m);
                    if (fd != null) {
                        fields.add(fd);
                    }
                }
            }
        }
        return new SectionDefinition(sectionId, title, repeatable, required, fields);
    }

    private static FieldDefinition parseField(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String fieldId = stringVal(raw.get("fieldId"));
        if (fieldId.isBlank()) {
            return null;
        }
        String label = stringVal(raw.get("label"));
        String type = stringVal(raw.get("type"));
        if (type.isBlank()) {
            type = "text";
        }
        boolean required = booleanVal(raw.get("required"));
        String hint = stringVal(raw.get("promptHint"));
        return new FieldDefinition(fieldId, label, type, required, hint);
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    private static boolean booleanVal(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof String s) {
            return "true".equalsIgnoreCase(s.trim());
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object x : list) {
            if (x != null) {
                out.add(x.toString().trim());
            }
        }
        return List.copyOf(out);
    }
}
