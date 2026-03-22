package com.vinekeepers.devops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One row from {@code deploy-targets.yaml}: Ansible playbook, optional Git remote for branch UX, optional Compose ops.
 */
public final class DeployTarget {

    private final String id;
    private final String label;
    private final String playbook;
    private final String gitRemote;
    private final Map<String, String> extraVars;
    private final DeployTargetCompose compose;

    public DeployTarget(String id, String label, String playbook, String gitRemote,
                        Map<String, String> extraVars, DeployTargetCompose compose) {
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
        this.compose = compose;
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

    public String getGitRemote() {
        return gitRemote;
    }

    public Map<String, String> getExtraVars() {
        return extraVars;
    }

    public DeployTargetCompose getCompose() {
        return compose;
    }

    /**
     * Valid when id is set and either an Ansible playbook path is set or compose ops are fully configured.
     */
    public boolean isValid() {
        if (id.isBlank()) {
            return false;
        }
        if (!playbook.isBlank()) {
            return true;
        }
        return compose.isConfigured();
    }

    public boolean hasAnsiblePlaybook() {
        return !playbook.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeployTarget that = (DeployTarget) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
