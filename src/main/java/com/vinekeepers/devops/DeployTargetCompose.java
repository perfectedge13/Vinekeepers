package com.vinekeepers.devops;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Optional Docker Compose metadata for a deploy target (allowlisted services, working directory).
 */
public final class DeployTargetCompose {

    public static final DeployTargetCompose NONE = new DeployTargetCompose("", "", List.of(), ComposeHostExecutor.DIRECT);

    private final String file;
    private final String workingDirectory;
    private final List<String> services;
    private final ComposeHostExecutor executor;

    public DeployTargetCompose(String file, String workingDirectory, List<String> services, ComposeHostExecutor executor) {
        this.file = file != null ? file.trim() : "";
        this.workingDirectory = workingDirectory != null ? workingDirectory.trim() : "";
        List<String> svc = services != null ? services.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList() : List.of();
        this.services = svc.isEmpty() ? List.of() : List.copyOf(svc);
        this.executor = executor != null ? executor : ComposeHostExecutor.DIRECT;
    }

    public String getFile() {
        return file;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public List<String> getServices() {
        return services;
    }

    public ComposeHostExecutor getExecutor() {
        return executor;
    }

    /** True when compose-driven ops are configured (path resolution still operator responsibility). */
    public boolean isConfigured() {
        return !file.isEmpty() && !workingDirectory.isEmpty() && !services.isEmpty();
    }

    public boolean allowsService(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return false;
        }
        String s = serviceId.trim();
        if ("_all".equals(s)) {
            return true;
        }
        return services.contains(s);
    }

    public List<String> allServiceNames() {
        return Collections.unmodifiableList(services);
    }
}
