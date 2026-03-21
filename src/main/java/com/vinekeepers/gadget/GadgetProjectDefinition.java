package com.vinekeepers.gadget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One deploy target from manifest YAML (playbook path, optional git remote for branch UX, pass-through Ansible vars).
 */
public final class GadgetProjectDefinition {

    private final String id;
    private final String label;
    private final String playbook;
    private final String gitRemote;
    private final Map<String, String> extraVars;

    public GadgetProjectDefinition(String id, String label, String playbook) {
        this(id, label, playbook, null, Map.of());
    }

    public GadgetProjectDefinition(String id, String label, String playbook, String gitRemote,
                                   Map<String, String> extraVars) {
        this.id = id != null ? id.trim() : "";
        this.label = label != null && !label.isBlank() ? label.trim() : this.id;
        this.playbook = playbook != null ? playbook.trim() : "";
        this.gitRemote = gitRemote != null && !gitRemote.isBlank() ? gitRemote.trim() : null;
        if (extraVars == null || extraVars.isEmpty()) {
            this.extraVars = Map.of();
        } else {
            Map<String, String> copy = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : extraVars.entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() != null) {
                    copy.put(e.getKey().trim(), e.getValue());
                }
            }
            this.extraVars = Collections.unmodifiableMap(copy);
        }
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getPlaybook() {
        return playbook;
    }

    /** Optional URL for {@code git ls-remote} branch listing; Ansible uses {@link #getExtraVars()} as needed. */
    public String getGitRemote() {
        return gitRemote;
    }

    /** Merged into {@code ansible-playbook -e} after neutral {@code project_id} / {@code branch} vars. */
    public Map<String, String> getExtraVars() {
        return extraVars;
    }

    public boolean isValid() {
        return !id.isBlank() && !playbook.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GadgetProjectDefinition that = (GadgetProjectDefinition) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
