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
        List<ReadinessAnyOfGroup> readiness = parseReadinessAnyOfGroups(raw.get("readiness"));
        boolean boundedChoices = parseBoundedClarificationChoices(raw.get("deliberation"));
        boolean inferOrHeuristic = parseInferBoundedChoiceFromOrInText(raw.get("deliberation"));
        List<String> forbidden = parsePacketQualityForbidden(raw.get("packetQuality"));
        CoordinatorClarificationSettings coord = parseCoordinatorClarification(raw.get("coordinatorClarification"));
        return new WorkProfileDefinition(
                profileId, title, artifacts, readiness, boundedChoices, inferOrHeuristic, forbidden, coord);
    }

    @SuppressWarnings("unchecked")
    private static CoordinatorClarificationSettings parseCoordinatorClarification(Object node) {
        if (!(node instanceof Map<?, ?> cm)) {
            return CoordinatorClarificationSettings.legacyDefault();
        }
        Map<String, Object> m = (Map<String, Object>) cm;
        String modeStr = stringVal(m.get("mode"));
        CoordinatorClarificationMode mode =
                "canonical_v1".equalsIgnoreCase(modeStr) ? CoordinatorClarificationMode.CANONICAL_V1 : CoordinatorClarificationMode.LEGACY;
        List<CoordinatorClarificationGapRule> gaps = new ArrayList<>();
        Object gapsObj = m.get("gaps");
        if (gapsObj instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> gm) {
                    CoordinatorClarificationGapRule r = parseCoordinatorGap((Map<String, Object>) gm);
                    if (r != null) {
                        gaps.add(r);
                    }
                }
            }
        }
        CoordinatorClarificationEnginePolicy engine = parseCoordinatorClarificationEngine(m);
        return new CoordinatorClarificationSettings(mode, gaps, engine);
    }

    @SuppressWarnings("unchecked")
    private static CoordinatorClarificationEnginePolicy parseCoordinatorClarificationEngine(Map<String, Object> coordinatorRoot) {
        if (coordinatorRoot == null) {
            return CoordinatorClarificationEnginePolicy.defaultPolicy();
        }
        Object engineNode = coordinatorRoot.get("engine");
        Map<String, Object> em =
                engineNode instanceof Map<?, ?> map ? (Map<String, Object>) map : coordinatorRoot;
        Integer maxTurnsObj = intObject(em.get("maxClarificationTurns"));
        int maxTurns =
                maxTurnsObj != null
                        ? maxTurnsObj
                        : CoordinatorClarificationEnginePolicy.defaultPolicy().getMaxClarificationTurns();
        Double threshold = doubleObject(em.get("repoEvidenceAskThreshold"));
        double thr =
                threshold != null
                        ? threshold
                        : CoordinatorClarificationEnginePolicy.defaultPolicy().getRepoEvidenceAskThreshold();
        boolean allowAssume =
                em.containsKey("allowAssumeAndContinue")
                        ? booleanVal(em.get("allowAssumeAndContinue"))
                        : CoordinatorClarificationEnginePolicy.defaultPolicy().isAllowAssumeAndContinue();
        boolean allowCrit =
                em.containsKey("allowClarificationAfterCritique")
                        ? booleanVal(em.get("allowClarificationAfterCritique"))
                        : CoordinatorClarificationEnginePolicy.defaultPolicy().isAllowClarificationAfterCritique();
        return new CoordinatorClarificationEnginePolicy(maxTurns, thr, allowAssume, allowCrit);
    }

    @SuppressWarnings("unchecked")
    private static CoordinatorClarificationGapRule parseCoordinatorGap(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String id = stringVal(raw.get("id"));
        if (id.isBlank()) {
            return null;
        }
        boolean blocking = booleanVal(raw.get("blocking"));
        String template = stringVal(raw.get("questionTemplate"));
        List<String> hintAll = stringList(raw.get("hintDetectAllOf"));
        List<String> canonOpen = stringList(raw.get("canonicalOpenAllOf"));
        List<String> resolveAny = stringList(raw.get("resolveAnySubstring"));
        boolean useBoundedUi = booleanVal(raw.get("useBoundedChoiceUi"));
        boolean inferOr = booleanVal(raw.get("inferOrChoices"));
        String narrowEsc = stringVal(raw.get("narrowEscalationTemplate"));
        return new CoordinatorClarificationGapRule(
                id, blocking, template, hintAll, canonOpen, resolveAny, useBoundedUi, inferOr, narrowEsc);
    }

    @SuppressWarnings("unchecked")
    private static List<String> parsePacketQualityForbidden(Object packetQualityNode) {
        if (!(packetQualityNode instanceof Map<?, ?> pm)) {
            return List.of();
        }
        return stringList(((Map<String, Object>) pm).get("forbiddenSubstrings"));
    }

    @SuppressWarnings("unchecked")
    private static boolean parseBoundedClarificationChoices(Object deliberationNode) {
        if (!(deliberationNode instanceof Map<?, ?> dm)) {
            return false;
        }
        Object v = ((Map<String, Object>) dm).get("boundedClarificationChoices");
        return booleanVal(v);
    }

    @SuppressWarnings("unchecked")
    private static boolean parseInferBoundedChoiceFromOrInText(Object deliberationNode) {
        if (!(deliberationNode instanceof Map<?, ?> dm)) {
            return false;
        }
        Object v = ((Map<String, Object>) dm).get("inferBoundedChoiceFromOrInText");
        return booleanVal(v);
    }

    @SuppressWarnings("unchecked")
    private static List<ReadinessAnyOfGroup> parseReadinessAnyOfGroups(Object readinessNode) {
        if (!(readinessNode instanceof Map<?, ?> rm)) {
            return List.of();
        }
        Object groups = rm.get("anyOfGroups");
        if (!(groups instanceof List<?> list)) {
            return List.of();
        }
        List<ReadinessAnyOfGroup> out = new ArrayList<>();
        for (Object gObj : list) {
            if (!(gObj instanceof Map<?, ?> gm)) {
                continue;
            }
            Object rulesObj = ((Map<String, Object>) gm).get("rules");
            if (!(rulesObj instanceof List<?> rlist)) {
                continue;
            }
            List<ReadinessPathRule> rules = new ArrayList<>();
            for (Object ro : rlist) {
                if (ro instanceof Map<?, ?> rm2) {
                    ReadinessPathRule r = parseReadinessPathRule((Map<String, Object>) rm2);
                    if (r != null) {
                        rules.add(r);
                    }
                }
            }
            if (!rules.isEmpty()) {
                out.add(new ReadinessAnyOfGroup(rules));
            }
        }
        return List.copyOf(out);
    }

    private static ReadinessPathRule parseReadinessPathRule(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }
        String artifactId = stringVal(raw.get("artifactId"));
        String sectionId = stringVal(raw.get("sectionId"));
        String fieldId = stringVal(raw.get("fieldId"));
        if (artifactId.isBlank() || sectionId.isBlank() || fieldId.isBlank()) {
            return null;
        }
        Integer minWords = intObject(raw.get("minWords"));
        Double maxEcho = doubleObject(raw.get("maxEchoOverlapWithRequest"));
        Integer echoSlack = intObject(raw.get("echoWordSlack"));
        boolean skipIfBlank = booleanVal(raw.get("skipIfBlank"));
        List<String> checks = stringList(raw.get("readinessChecks"));
        return new ReadinessPathRule(
                artifactId, sectionId, fieldId, minWords, maxEcho, echoSlack, skipIfBlank, checks);
    }

    private static Integer intObject(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Integer.parseInt(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double doubleObject(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
        Integer minWords = intObject(raw.get("minWords"));
        Double maxEcho = doubleObject(raw.get("maxEchoOverlapWithRequest"));
        Integer echoSlack = intObject(raw.get("echoWordSlack"));
        boolean skipIfBlank = booleanVal(raw.get("skipReadinessIfBlank"));
        List<String> checks = stringList(raw.get("readinessChecks"));
        return new FieldDefinition(
                fieldId, label, type, required, hint, minWords, maxEcho, echoSlack, skipIfBlank, checks);
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
