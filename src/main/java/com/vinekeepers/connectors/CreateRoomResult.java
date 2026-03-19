package com.vinekeepers.connectors;

/**
 * Result of SpaceOperations.createRoom: either success with channel id or failure with reason.
 * Actions translate to String (id or sentinel) for run() return.
 */
public sealed interface CreateRoomResult permits CreateRoomResult.Success, CreateRoomResult.Failure {

    boolean isSuccess();

    /** Channel id when success; null when failure. */
    String getChannelId();

    /** Failure reason when failure; null when success. */
    CreateRoomFailureReason getFailureReason();

    record Success(String channelId) implements CreateRoomResult {
        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public String getChannelId() {
            return channelId;
        }

        @Override
        public CreateRoomFailureReason getFailureReason() {
            return null;
        }
    }

    record Failure(CreateRoomFailureReason reason) implements CreateRoomResult {
        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public String getChannelId() {
            return null;
        }

        @Override
        public CreateRoomFailureReason getFailureReason() {
            return reason;
        }
    }
}
