package com.vinekeepers.workflow.planning;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Deterministic "request expansion" text: explores what a feature request implies before the planning packet.
 */
public final class RequestExplorationSupport {

    private static final int SNIPPET_MAX = 800;

    private RequestExplorationSupport() {
    }

    public static String buildExplorationBody(String request, String readmeSnippet, List<String> sampleFiles) {
        String req = request != null ? request.trim() : "";
        String norm = req.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        sb.append("(Auto-generated exploration — edit in proposals or thread if wrong.)\n\n");

        sb.append("## Workflow / execution touchpoints\n");
        sb.append(bulletLine(touchpoints(norm, sampleFiles)));
        sb.append("\n\n## Configuration model\n");
        sb.append(bulletLine(configModel(norm)));
        sb.append("\n\n## Provider, model, and selection granularity\n");
        sb.append(bulletLine(providerModel(norm)));
        sb.append("\n\n## Runtime vs configuration-only change\n");
        sb.append(bulletLine(runtimeVsConfig(norm)));
        sb.append("\n\n## Schema, spec, and YAML impacts\n");
        sb.append(bulletLine(schemaImpacts(norm, sampleFiles)));
        sb.append("\n\n## Validation and quality gates\n");
        sb.append(bulletLine(validation(norm)));
        sb.append("\n\n## Backward compatibility and fallback\n");
        sb.append(bulletLine(compatFallback(norm)));
        sb.append("\n\n## Likely impacted components (repo hints)\n");
        sb.append(bulletLine(components(sampleFiles)));
        if (readmeSnippet != null && !readmeSnippet.isBlank()) {
            sb.append("\n\n## README context (snippet)\n");
            String snip = readmeSnippet.length() > SNIPPET_MAX
                    ? readmeSnippet.substring(0, SNIPPET_MAX) + "…"
                    : readmeSnippet;
            sb.append(snip);
        }
        return sb.toString().trim();
    }

    private static String bulletLine(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "- _Confirm with the team — no automatic signal._";
        }
        return lines.stream().map(l -> "- " + l).collect(Collectors.joining("\n"));
    }

    private static List<String> touchpoints(String norm, List<String> files) {
        List<String> out = new ArrayList<>();
        if (norm.contains("workflow") || norm.contains("step")) {
            out.add("Maps to configurable workflows / steps — identify which `workflowRef` or Java workflow actions are involved.");
        }
        if (norm.contains("discord") || norm.contains("thread") || norm.contains("button")) {
            out.add("Discord ingress: thread vs channel, interactions (`present_choices`), and `ConfigurableWorkflowRunner` steps.");
        }
        if (norm.contains("cursor") || norm.contains("agent")) {
            out.add("Cursor Cloud lifecycle: launch, monitor, and state keys under `LifecycleRunRecord`.");
        }
        if (files.stream().anyMatch(p -> p.contains("workflow"))) {
            out.add("Repo contains workflow YAML or Java under workflow packages — enumerate call sites.");
        }
        if (out.isEmpty()) {
            out.add("List concrete user-visible or API touchpoints (screens, events, commands) implied by the request.");
        }
        return out;
    }

    private static List<String> configModel(String norm) {
        List<String> out = new ArrayList<>();
        if (norm.contains("per-step") || norm.contains("per step") || norm.contains("each step")) {
            out.add("Per-step overrides appear required — specify which steps and how defaults merge.");
        }
        if (norm.contains("global") || norm.contains("default")) {
            out.add("Global defaults may apply — clarify override precedence (global → per-step → runtime).");
        }
        if (norm.contains("yaml") || norm.contains("config")) {
            out.add("YAML/config-driven behavior — identify files (`config/bots.yaml`, env, work profiles) and loaders.");
        }
        if (out.isEmpty()) {
            out.add("Clarify: per-component, per-step, global default, or combined; where values live (files vs DB vs env).");
        }
        return out;
    }

    private static List<String> providerModel(String norm) {
        List<String> out = new ArrayList<>();
        if (norm.contains("llm") || norm.contains("model") || norm.contains("openai") || norm.contains("anthropic")) {
            out.add("Distinguish provider vs model ID vs provider+model tuple; document selection UI or config shape.");
        }
        if (norm.contains("plug") || norm.contains("adapter") || norm.contains("swap")) {
            out.add("Swappable implementations — define interface boundaries and discovery/registration mechanism.");
        }
        if (out.isEmpty()) {
            out.add("If not model-related: state N/A; else specify selectable dimensions and storage.");
        }
        return out;
    }

    private static List<String> runtimeVsConfig(String norm) {
        List<String> out = new ArrayList<>();
        if (norm.contains("reload") || norm.contains("runtime") || norm.contains("hot")) {
            out.add("Runtime toggles may need code paths for live reload vs restart — call out threading and safety.");
        }
        if (norm.contains("only config") || norm.contains("config-only") || norm.contains("yaml only")) {
            out.add("Config-only change: no Java changes expected — list exact files and validation.");
        }
        if (out.isEmpty()) {
            out.add("Separate: behavior change requiring code vs values change in config/spec only.");
        }
        return out;
    }

    private static List<String> schemaImpacts(String norm, List<String> files) {
        List<String> out = new ArrayList<>();
        if (norm.contains("spec") || norm.contains("schema") || norm.contains("registry")) {
            out.add("Spec/registry updates: `specs/*.yml`, `npm run validate-specs`, `validate-drift`, workflow-registry entries.");
        }
        if (files.stream().anyMatch(p -> p.endsWith(".yml") || p.endsWith(".yaml"))) {
            out.add("YAML sources present in repo — bump workflow or profile docs when adding keys.");
        }
        if (out.isEmpty()) {
            out.add("Note any schema, OpenAPI, or internal DSL that must version alongside the feature.");
        }
        return out;
    }

    private static List<String> validation(String norm) {
        List<String> out = new ArrayList<>();
        out.add("Minimum: `mvn test`, `mvn compile`, and `npm run validate-specs` / `validate-drift` when specs touched.");
        if (norm.contains("discord") || norm.contains("workflow")) {
            out.add("Add or extend workflow runner tests for new steps or branch indices.");
        }
        return out;
    }

    private static List<String> compatFallback(String norm) {
        List<String> out = new ArrayList<>();
        out.add("Document default behavior when new config absent (backward compatible rollouts).");
        if (norm.contains("fallback") || norm.contains("degrad")) {
            out.add("Explicit degradation path requested — tie to monitoring and user-visible errors.");
        }
        return out;
    }

    private static List<String> components(List<String> sampleFiles) {
        if (sampleFiles == null || sampleFiles.isEmpty()) {
            return List.of("Run a quick code search after clone — sample file list was empty at exploration time.");
        }
        Set<String> top = new LinkedHashSet<>();
        for (String p : sampleFiles) {
            int slash = p.indexOf('/');
            String prefix = slash > 0 ? p.substring(0, slash) : p;
            if (!prefix.isBlank()) {
                top.add(prefix);
            }
            if (top.size() >= 8) {
                break;
            }
        }
        return List.of(String.join(", ", top) + " (sample paths only — refine after review).");
    }

    /** Heuristic: exploration looks unedited and too thin for approval. */
    public static boolean explorationLooksShallow(String body) {
        if (body == null) {
            return true;
        }
        String t = body.trim();
        if (t.length() < 180) {
            return true;
        }
        long hashHeadings = t.lines().filter(l -> l.startsWith("## ")).count();
        return hashHeadings < 4;
    }

    /** True if summary is mostly a restatement of the original request (anti-echo). */
    public static boolean summaryMostlyEchoesRequest(String request, String featureSummary) {
        if (request == null || featureSummary == null) {
            return false;
        }
        String r = request.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String s = featureSummary.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (r.length() < 20 || s.length() < 20) {
            return false;
        }
        if (s.length() < r.length() * 0.85) {
            return false;
        }
        return r.contains(s) || s.contains(r) || sharedTokenRatio(r, s) > 0.82;
    }

    private static double sharedTokenRatio(String a, String b) {
        String[] ta = a.split("[^a-z0-9]+");
        String[] tb = b.split("[^a-z0-9]+");
        Set<String> sa = new LinkedHashSet<>();
        for (String x : ta) {
            if (x.length() > 2) {
                sa.add(x);
            }
        }
        if (sa.isEmpty()) {
            return 0;
        }
        int hit = 0;
        Set<String> sb = new LinkedHashSet<>();
        for (String x : tb) {
            if (x.length() > 2) {
                sb.add(x);
            }
        }
        for (String x : sa) {
            if (sb.contains(x)) {
                hit++;
            }
        }
        return (double) hit / sa.size();
    }

    public static boolean riskSummaryMentionsMitigation(String riskSummary) {
        if (riskSummary == null) {
            return false;
        }
        String n = riskSummary.toLowerCase(Locale.ROOT);
        return n.contains("mitigat") || n.contains("fallback") || n.contains("rollback")
                || n.contains("roll-back") || n.contains("contingency");
    }

    public static String readSnippet(Path root, int maxChars) {
        if (root == null || !Files.isDirectory(root)) {
            return "";
        }
        for (String name : List.of("README.md", "README.MD", "readme.md")) {
            Path p = root.resolve(name);
            if (Files.isRegularFile(p)) {
                try {
                    String text = Files.readString(p);
                    return text.length() > maxChars ? text.substring(0, maxChars) + "…" : text;
                } catch (Exception e) {
                    return "";
                }
            }
        }
        return "";
    }
}
