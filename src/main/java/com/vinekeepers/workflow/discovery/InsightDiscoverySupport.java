package com.vinekeepers.workflow.discovery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vinekeepers.profile.ArtifactDefinition;
import com.vinekeepers.profile.FieldDefinition;
import com.vinekeepers.profile.SectionDefinition;
import com.vinekeepers.profile.WorkProfileDefinition;
import com.vinekeepers.state.planning.DiscoveryAgenda;
import com.vinekeepers.state.planning.DiscoveryGap;
import com.vinekeepers.state.planning.DiscoveryQuestion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bundles multiple required-field gaps into one insight-style prompt and parses bundled replies.
 */
public final class InsightDiscoverySupport {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Map<String, String>>> TARGET_LIST = new TypeReference<>() {};

    private InsightDiscoverySupport() {}

    public record BundledTarget(String artifactId, String sectionId, String fieldId, String label) {}

    /**
     * When there are at least two REQUIRED_FIELD gaps and no BLOCKER gaps, build one prompt and bundled apply.
     */
    public static Map<String, Object> buildInsightAgendaSpread(
            String gapsJson,
            WorkProfileDefinition profile,
            String initialRequest) throws JsonProcessingException {
        List<DiscoveryGap> gaps = StructuredDiscoverySupport.parseGapsJson(gapsJson);
        boolean blocker = gaps.stream().anyMatch(g -> "BLOCKER".equalsIgnoreCase(g.getSeverity()));
        List<DiscoveryGap> requiredAll = gaps.stream()
                .filter(g -> "REQUIRED_FIELD".equalsIgnoreCase(g.getKind()))
                .sorted(StructuredDiscoverySupport.GAP_COMPARATOR)
                .toList();
        if (blocker || requiredAll.size() < 2 || profile == null) {
            return StructuredDiscoverySupport.buildAgendaSpread(gapsJson);
        }
        int cap = Math.min(3, requiredAll.size());
        List<DiscoveryGap> required = requiredAll.subList(0, cap);

        List<BundledTarget> targets = new ArrayList<>();
        for (DiscoveryGap g : required) {
            String label = resolveFieldLabel(profile, g.getArtifactId(), g.getSectionId(), g.getFieldId());
            targets.add(new BundledTarget(g.getArtifactId(), g.getSectionId(), g.getFieldId(), label));
        }

        String prompt = buildBundledPrompt(initialRequest, targets);
        List<Map<String, String>> jsonTargets = new ArrayList<>();
        for (BundledTarget t : targets) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("artifactId", t.artifactId());
            m.put("sectionId", t.sectionId());
            m.put("fieldId", t.fieldId());
            m.put("label", t.label());
            jsonTargets.add(m);
        }

        DiscoveryQuestion q = new DiscoveryQuestion(
                "q-bundled-1",
                "bundled",
                "orchestrator",
                "HIGH",
                prompt,
                "BUNDLED",
                "",
                "",
                "",
                "replace");

        DiscoveryAgenda agenda = new DiscoveryAgenda(
                List.of(q),
                gaps.size(),
                q.getQuestionId(),
                Instant.now().toString());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("discoveryAgendaJson", JSON.writeValueAsString(agenda));
        m.put("discoveryCurrentQuestionPrompt", prompt);
        m.put("discoveryCurrentQuestionId", q.getQuestionId());
        m.put("discoveryApplyKind", "BUNDLED");
        m.put("discoveryApplyArtifactId", "");
        m.put("discoveryApplySectionId", "");
        m.put("discoveryApplyFieldId", "");
        m.put("discoveryApplyMode", "replace");
        m.put("discoveryBundledApplyJson", JSON.writeValueAsString(jsonTargets));
        return m;
    }

    /**
     * Apply user answer to each bundled target using **Label** sections; falls back to whole text on first field only.
     */
    public static List<Map<String, String>> parseBundledAnswer(String answer, List<Map<String, String>> targets) {
        List<Map<String, String>> out = new ArrayList<>();
        if (answer == null || answer.isBlank() || targets == null || targets.isEmpty()) {
            return out;
        }
        String text = answer.trim();
        boolean any = false;
        for (Map<String, String> t : targets) {
            String label = t.get("label");
            String artifactId = t.get("artifactId");
            String sectionId = t.get("sectionId");
            String fieldId = t.get("fieldId");
            if (label == null || artifactId == null || sectionId == null || fieldId == null) {
                continue;
            }
            String extracted = extractUnderLabel(text, label);
            if (extracted != null && !extracted.isBlank()) {
                any = true;
                Map<String, String> row = new LinkedHashMap<>();
                row.put("artifactId", artifactId);
                row.put("sectionId", sectionId);
                row.put("fieldId", fieldId);
                row.put("value", extracted.trim());
                out.add(row);
            }
        }
        if (!any && !text.isBlank()) {
            List<Map<String, String>> numbered = parseNumberedAnswers(text, targets);
            if (!numbered.isEmpty()) {
                return numbered;
            }
            Map<String, String> first = targets.get(0);
            Map<String, String> row = new LinkedHashMap<>();
            row.put("artifactId", first.get("artifactId"));
            row.put("sectionId", first.get("sectionId"));
            row.put("fieldId", first.get("fieldId"));
            row.put("value", text);
            out.add(row);
        }
        return out;
    }

    /** Maps "1. ... 2. ..." lines to targets in order. */
    private static List<Map<String, String>> parseNumberedAnswers(String text, List<Map<String, String>> targets) {
        List<Map<String, String>> parsed = new ArrayList<>();
        if (text == null || targets == null || targets.isEmpty()) {
            return parsed;
        }
        Pattern linePat = Pattern.compile("(?m)^\\s*(\\d+)\\.\\s*(.+)$");
        Matcher lm = linePat.matcher(text);
        List<String> answers = new ArrayList<>();
        while (lm.find()) {
            answers.add(lm.group(2).trim());
        }
        if (answers.size() < targets.size()) {
            return List.of();
        }
        for (int i = 0; i < targets.size(); i++) {
            Map<String, String> t = targets.get(i);
            String v = answers.get(i);
            if (v == null || v.isBlank()) {
                continue;
            }
            Map<String, String> row = new LinkedHashMap<>();
            row.put("artifactId", t.get("artifactId"));
            row.put("sectionId", t.get("sectionId"));
            row.put("fieldId", t.get("fieldId"));
            row.put("value", v);
            parsed.add(row);
        }
        return parsed;
    }

    public static List<Map<String, String>> parseBundledTargetsJson(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return JSON.readValue(json, TARGET_LIST);
    }

    private static String extractUnderLabel(String text, String label) {
        String quoted = Pattern.quote(label);
        Pattern p = Pattern.compile(
                "(?is)\\*\\*" + quoted + "\\*\\*\\s*[\\:\\-]?\\s*(.+?)(?=\\n\\*\\*[\\w\\s/]+\\*\\*|$)");
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        // Line-oriented: Label: value
        Pattern p2 = Pattern.compile("(?is)(?:^|\\n)" + quoted + "\\s*[\\:\\-]\\s*(.+?)(?=\\n[A-Za-z][^\\n]{0,60}\\s*[\\:\\-]|$)");
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return m2.group(1).trim();
        }
        return null;
    }

    private static String buildBundledPrompt(String initialRequest, List<BundledTarget> targets) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Planning check-in** — a few focused questions so we can firm up the packet.\n\n");
        if (initialRequest != null && !initialRequest.isBlank()) {
            String rq = initialRequest.trim();
            sb.append("**Your request:** ")
                    .append(rq.length() > 400 ? rq.substring(0, 400) + "…" : rq)
                    .append("\n\n");
        }
        int n = 1;
        for (BundledTarget t : targets) {
            sb.append(n++)
                    .append(". ")
                    .append(pmQuestion(t.fieldId(), t.label()))
                    .append("\n\n");
        }
        sb.append(
                "Reply in **one** message. Either number your answers (1., 2., …) to match, or use **")
                .append(targets.get(0).label())
                .append("**-style headings with detail under each.");
        return sb.toString();
    }

    private static String pmQuestion(String fieldId, String label) {
        if (fieldId == null) {
            return "Clarify **" + label + "** for this plan.";
        }
        return switch (fieldId) {
            case "feature_summary" -> "What problem are we solving and who benefits when this ships?";
            case "scope_summary" -> "What is in scope vs explicitly out of scope? Anything that must stay backward-compatible?";
            case "acceptance_criteria" -> "What must be true when we’re done (testable outcomes / checks)?";
            case "user_stories" -> "What are the main user flows or scenarios we should not miss?";
            case "components_impacted" -> "Which parts of the codebase or services are likely to change?";
            case "architecture_summary" -> "What approach and constraints should we follow (integration points, data, APIs)?";
            case "risk_summary" -> "What could go wrong, and how should we roll back or mitigate?";
            case "open_questions" -> "What decisions or unknowns still need an owner or answer before implementation?";
            case "plan_body" -> "What’s the phased implementation order that matches this repo?";
            case "validation_notes" -> "How will we prove this works (tests, manual checks, gates)?";
            case "context_summary" -> "What repo or environment context should implementers know?";
            default -> "Help us flesh out **" + label + "** with concrete detail.";
        };
    }

    private static String resolveFieldLabel(
            WorkProfileDefinition profile,
            String artifactId,
            String sectionId,
            String fieldId) {
        ArtifactDefinition art = profile.getArtifactsById().get(artifactId);
        if (art == null) {
            return fieldId;
        }
        for (SectionDefinition sec : art.getSections()) {
            if (!sec.getSectionId().equals(sectionId)) {
                continue;
            }
            for (FieldDefinition f : sec.getFields()) {
                if (f.getFieldId().equals(fieldId)) {
                    if (f.getLabel() != null && !f.getLabel().isBlank()) {
                        return f.getLabel();
                    }
                }
            }
        }
        return fieldId;
    }

}
