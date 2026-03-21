package com.vinekeepers.gadget;

import java.util.Objects;

/**
 * One deployable project for Gadget (from {@code config/gadget-projects.yaml}).
 */
public final class GadgetProjectDefinition {

    private final String id;
    private final String label;
    private final String playbook;

    public GadgetProjectDefinition(String id, String label, String playbook) {
        this.id = id != null ? id.trim() : "";
        this.label = label != null && !label.isBlank() ? label.trim() : this.id;
        this.playbook = playbook != null ? playbook.trim() : "";
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
