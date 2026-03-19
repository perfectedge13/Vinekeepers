package com.vinekeepers.connectors;

/**
 * Result of SpaceOperations.createThread: either success with thread id or failure with reason.
 * Actions translate to String (id or sentinel) for run() return.
 */
public sealed interface CreateThreadResult permits CreateThreadResult.Success, CreateThreadResult.Failure {

    boolean isSuccess();

    /** Thread id when success; null when failure. */
    String getThreadId();

    /** Failure reason when failure; null when success. */
    CreateThreadFailureReason getFailureReason();

    record Success(String threadId) implements CreateThreadResult {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public String getThreadId() {
            return threadId;
        }

        @Override
        public CreateThreadFailureReason getFailureReason() {
            return null;
        }
    }

    record Failure(CreateThreadFailureReason reason) implements CreateThreadResult {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public String getThreadId() {
            return null;
        }

        @Override
        public CreateThreadFailureReason getFailureReason() {
            return reason;
        }
    }
}
