package com.vinekeepers.providers;

import com.vinekeepers.events.Event;
import com.vinekeepers.gadget.GadgetProjectDefinition;
import com.vinekeepers.gadget.GadgetProjectRegistry;
import com.vinekeepers.interactions.ResponseIntent;
import com.vinekeepers.workflow.ConfigurableWorkflowState;
import com.vinekeepers.workflow.DynamicChoiceProvider;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Branch names from {@code git ls-remote --heads} using the selected project's {@link GadgetProjectDefinition#getGitRemote()}.
 */
public final class GitRemoteBranchesChoiceProvider implements DynamicChoiceProvider {

    static final int MAX_BRANCHES = 40;
    static final int TIMEOUT_SECS = 15;

    private final GadgetProjectRegistry registry;

    public GitRemoteBranchesChoiceProvider(GadgetProjectRegistry registry) {
        this.registry = registry != null ? registry : new GadgetProjectRegistry(List.of());
    }

    @Override
    public List<ResponseIntent.Choice> getChoices(Event event, ConfigurableWorkflowState state) {
        String projectId = stringFromState(state, "gadgetProject");
        if (projectId == null || projectId.isBlank()) {
            return fallbackChoices();
        }
        return registry.findById(projectId)
                .map(this::choicesForProject)
                .orElseGet(GitRemoteBranchesChoiceProvider::fallbackChoices);
    }

    private List<ResponseIntent.Choice> choicesForProject(GadgetProjectDefinition project) {
        String remote = project.getGitRemote();
        if (remote == null || remote.isBlank()) {
            return fallbackChoices();
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "git", "ls-remote", "--heads", remote);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            if (!proc.waitFor(TIMEOUT_SECS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return fallbackChoices();
            }
            if (proc.exitValue() != 0) {
                return fallbackChoices();
            }
            Set<String> names = new LinkedHashSet<>();
            for (String line : out.toString().split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                int tab = t.indexOf('\t');
                if (tab < 0) {
                    continue;
                }
                String ref = t.substring(tab + 1).trim();
                String prefix = "refs/heads/";
                if (ref.startsWith(prefix)) {
                    names.add(ref.substring(prefix.length()));
                }
            }
            if (names.isEmpty()) {
                return fallbackChoices();
            }
            List<String> sorted = new ArrayList<>(names);
            sorted.sort(Comparator.comparing(a -> a.toLowerCase(Locale.ROOT)));
            if (sorted.size() > MAX_BRANCHES) {
                sorted = sorted.subList(0, MAX_BRANCHES);
            }
            List<ResponseIntent.Choice> choices = new ArrayList<>();
            for (String b : sorted) {
                choices.add(new ResponseIntent.Choice(b, b, null));
            }
            choices.add(new ResponseIntent.Choice("other", "Other (type branch name)", null));
            return choices;
        } catch (Exception e) {
            return fallbackChoices();
        }
    }

    private static List<ResponseIntent.Choice> fallbackChoices() {
        return List.of(
                new ResponseIntent.Choice("main", "main", null),
                new ResponseIntent.Choice("develop", "develop", null),
                new ResponseIntent.Choice("other", "Other (type branch name)", null));
    }

    private static String stringFromState(ConfigurableWorkflowState state, String key) {
        if (state == null || key == null) {
            return null;
        }
        Object v = state.get(key);
        return v != null ? v.toString().trim() : null;
    }
}
