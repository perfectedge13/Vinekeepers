package com.vinekeepers.audit;

/**
 * Records audit log entries (e.g. to memory, file, or log).
 */
@FunctionalInterface
public interface AuditRecorder {

    void record(AuditLog entry);
}
