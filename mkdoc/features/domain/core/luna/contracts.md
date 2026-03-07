# Contracts

- **GatheringState (ex-LunaConversationState):** step (enum: AWAITING_PROJECT, AWAITING_CHANGE, READY_TO_RUN, DONE), project (string), codeChangeDescription (string). Immutable; withProject/withCodeChange/withStep return new instances.
- **CursorCloudAdapter:** createBranch(projectPathOrId, branchName), runNovaCommit(projectPathOrId, changeDescription), push(projectPathOrId), createPr(projectPathOrId, title). Implementation is env-based; stub behavior until real Cursor Cloud API integration.
