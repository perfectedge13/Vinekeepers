# Tests

# Coverage

Unit tests cover workflow definition parsing, step result helpers, workflow action registry behavior, and the shipped step implementations used by configured workflows.

# Test list

| Test ID | Title | Class | Method | Intent |
|---------|-------|-------|--------|--------|
| UNIT-WORKFLOW-DEFINITION | WorkflowDefinitionTest | com.vinekeepers.workflow.WorkflowDefinitionTest | — | Verify `WorkflowDefinition` id and steps structure |
| UNIT-STEP-RESULT | StepResultTest | com.vinekeepers.workflow.StepResultTest | — | Verify `StepResult` factory methods and outcome normalization |
| UNIT-WORKFLOW-ACTION-REGISTRY | WorkflowActionRegistryTest | com.vinekeepers.workflow.WorkflowActionRegistryTest | — | Verify action registration, resolution, execution, and safe failure handling |
| UNIT-ASK-FOR-INPUT-STEP | AskForInputStepTest | com.vinekeepers.workflow.steps.AskForInputStepTest | — | Verify `ask_input` stores user input in state |
| UNIT-CALL-ACTION-STEP | CallActionStepTest | com.vinekeepers.workflow.steps.CallActionStepTest | — | Verify `call_action` invokes the registered action |
| UNIT-BRANCH-STEP | BranchStepTest | com.vinekeepers.workflow.steps.BranchStepTest | — | Verify BranchStep with clear config returns StepResult with clearKeys; branch without clear returns empty clearKeys |
| UNIT-BRANCH-STEP-BLANK-VALUE | BranchStepTest | com.vinekeepers.workflow.steps.BranchStepTest | branchWithEmptyStringValueMatchesEmptyState | Verify BranchStep when/value "" matches empty string from capture_field (blank branch e.g. arrietty_room) |
| UNIT-CAPTURE-FIELD-STEP | CaptureFieldFromEventStepTest | com.vinekeepers.workflow.steps.CaptureFieldFromEventStepTest | — | Verify CaptureFieldFromEventStep reads interaction values (nested or direct list) and customId fallback |
| UNIT-CAPTURE-FIELD-STEP-TRIM-LOWER | CaptureFieldFromEventStepTest | com.vinekeepers.workflow.steps.CaptureFieldFromEventStepTest | messageEventWithTrimAndLowerTrimsAndLowercasesContent | Verify capture_field trimAndLower trims and lowercases message content; trimAndLower false or absent leaves content unchanged |
| UNIT-WORKFLOW-TRANSFORMS | WorkflowTransformsTest | com.vinekeepers.workflow.WorkflowTransformsTest | — | Verify WorkflowTransforms trim, lower, upper, default; null/blank-safe behavior |
| UNIT-WORKFLOW-CONDITION-EVALUATOR | WorkflowConditionEvaluatorTest | com.vinekeepers.workflow.WorkflowConditionEvaluatorTest | — | Verify WorkflowConditionEvaluator operators equals, not_equals, blank, nonblank, contains, starts_with, regex, one_of |
| UNIT-EXTRACT-EVENT-FIELDS-STEP | ExtractEventFieldsStepTest | com.vinekeepers.workflow.steps.ExtractEventFieldsStepTest | — | Verify extract_event step extracts from payload/context into state with from, storeIn, default, transforms |
| UNIT-CONFIGURABLE-WORKFLOW-RUNNER-TRIM-LOWER | ConfigurableWorkflowRunnerTest | com.vinekeepers.workflow.ConfigurableWorkflowRunnerTest | runCaptureFieldWithTrimAndLowerStoresTrimmedAndLowercasedValue | Verify configurable workflow with capture_field and trimAndLower (e.g. arrietty_room-style) stores trimmed and lowercased value |
| UNIT-CONFIGURABLE-WORKFLOW-RUNNER | ConfigurableWorkflowRunnerTest | com.vinekeepers.workflow.ConfigurableWorkflowRunnerTest | — | Verify ConfigurableWorkflowRunner executes steps and applies clearKeys for edit-reprompt |
| UNIT-DONE-STEP | DoneStepTest | com.vinekeepers.workflow.steps.DoneStepTest | — | Verify `done` completes with message and template support |
| UNIT-POST-CHANNEL-MESSAGE-INTERPOLATION | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | runInterpolatesContentFromBindState | Verify post_channel_message interpolates content from merged map (state then bind, bind overrides) |
| UNIT-POST-CHANNEL-MESSAGE-AS-ROLE | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | runWithAsRole_usesOutboundDeliveryRouterSendAsRole | Verify post_channel_message with asRole uses OutboundDeliveryRouter.sendAsRole |
| UNIT-POST-CHANNEL-MESSAGE-AS-ROLE-BIND-OVERRIDES | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | runWithAsRoleFromBind_overridesState | Verify post_channel_message asRole from bind overrides state |
| UNIT-PROVISION-ROOM-PARTICIPANTS-ACTION | ProvisionRoomParticipantsActionTest | com.vinekeepers.workflow.actions.ProvisionRoomParticipantsActionTest | — | Verify provision_room_participants provisions four participants into state |
| UNIT-INITIALIZE-FEATURE-ROOM-STATE-ACTION | InitializeFeatureRoomStateActionTest | com.vinekeepers.workflow.actions.InitializeFeatureRoomStateActionTest | — | Verify initialize_feature_room_state builds and stores FeatureRoomState from bind/state |
| UNIT-CREATE-LIFECYCLE-CONTEXT-BIND-PRECEDENCE | CreateLifecycleContextActionTest | com.vinekeepers.workflow.actions.CreateLifecycleContextActionTest | runPrefersBindOverStateForNewFields | Verify create_lifecycle_context uses bind precedence (bind overrides state) |
| UNIT-CREATE-CHANNEL-ACTION | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | — | Verify create_channel action uses gateway createTextChannel for lifecycle room |
| UNIT-CREATE-CHANNEL-ACTION-SENTINEL | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | runReturnsChannelCreateFailedWhenCreateTextChannelReturnsNull | Verify create_channel returns CHANNEL_CREATE_FAILED on gateway failure or null |
| UNIT-CREATE-CHANNEL-ACTION-NORMALIZE | CreateChannelActionTest | com.vinekeepers.workflow.actions.CreateChannelActionTest | runNormalizesChannelNameFromBindToLowercaseDiscordSafe | Verify create_channel normalizes channel name to Discord-safe lowercase |
| UNIT-POST-CHANNEL-MESSAGE-ACTION | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | — | Verify post_channel_message action posts to Discord channel |
| UNIT-POST-CHANNEL-MESSAGE-LIFECYCLE-BOT-NAME | PostChannelMessageActionTest | com.vinekeepers.workflow.actions.PostChannelMessageActionTest | runInterpolatesLifecycleBotNameFromMergedMapBindWins | Verify post_channel_message interpolates lifecycleBotName from merged map (bind wins) |
| UNIT-POST-PLANNING-PROGRESS-IF-CHANGED | PostPlanningProgressIfChangedActionTest | com.vinekeepers.workflow.actions.PostPlanningProgressIfChangedActionTest | skipsSendWhenNotWorthy | Verify post_planning_progress_if_changed skips send when planningProgressPostWorthy is false |
| UNIT-POST-PLANNING-PROGRESS-IF-CHANGED-HASH | PostPlanningProgressIfChangedActionTest | com.vinekeepers.workflow.actions.PostPlanningProgressIfChangedActionTest | postsAndRecordsHashWhenWorthy | Verify post_planning_progress_if_changed posts and stores planningLastProgressPostHash on OK |
| UNIT-OPENAI-CHAT-CLIENT | OpenAiChatClientTest | com.vinekeepers.connectors.openai.OpenAiChatClientTest | — | Verify planning OpenAI client progress notification, optional body logging toggles, truncation/redaction, and error paths |
| UNIT-OPENAI-PLANNING-PROGRESS-POSTER | OpenAiPlanningProgressPosterTest | com.vinekeepers.workflow.actions.OpenAiPlanningProgressPosterTest | — | Verify OpenAiPlanningProgressPoster posts **Update:** lines via PostChannelMessageAction when Discord progress env is enabled |
| UNIT-PROVISION-BOT-INSTANCE-ACTION | ProvisionBotInstanceActionTest | com.vinekeepers.workflow.actions.ProvisionBotInstanceActionTest | — | Verify provision_bot_instance action provisions runtime bot instance |
| UNIT-CREATE-LIFECYCLE-CONTEXT-ACTION | CreateLifecycleContextActionTest | com.vinekeepers.workflow.actions.CreateLifecycleContextActionTest | — | Verify create_lifecycle_context action creates and stores lifecycle context |
| UNIT-CREATE-LIFECYCLE-CONTEXT-BIND-WINS | CreateLifecycleContextActionTest | com.vinekeepers.workflow.actions.CreateLifecycleContextActionTest | runConfiguredBotIdBindPrecedenceBindWinsOverState | Verify create_lifecycle_context bind precedence (bind overrides state for configuredBotId) |
| UNIT-LAUNCH-CURSOR-RUN-ACTION | LaunchCursorRunActionTest | com.vinekeepers.workflow.actions.LaunchCursorRunActionTest | runLaunchesAndStoresRecordAndBindsContext | Verify launch_cursor_run action launches run, registers run record, and acknowledges to Discord with status (e.g. launching) and lifecycle room |
| UNIT-CREATE-THREAD-ACTION | CreateThreadActionTest | com.vinekeepers.workflow.actions.CreateThreadActionTest | — | Verify create_thread delegates to SpaceOperationsRegistry by source prefix; THREAD_CREATE_FAILED when no ops or failure |
| UNIT-SPACE-OPERATIONS-REGISTRY | SpaceOperationsRegistryTest | com.vinekeepers.connectors.SpaceOperationsRegistryTest | — | Verify SpaceOperationsRegistry register and get by connector id |
| UNIT-DISCORD-SPACE-OPERATIONS | DiscordSpaceOperationsTest | com.vinekeepers.connectors.DiscordSpaceOperationsTest | — | Verify DiscordSpaceOperations createRoom and createThread (normalizeChannelName, setDeliveryTargetId) |
| UNIT-PLANNING-RAG-CONFIG | PlanningRagConfigTest | com.vinekeepers.workflow.planning.rag.PlanningRagConfigTest | — | Verify planning RAG env toggles, Qdrant URL resolution, graceful disable when configuration is incomplete |
| UNIT-REPO-GROUNDING-PARQUET-STORE | RepoGroundingParquetStoreTest | com.vinekeepers.workflow.planning.rag.RepoGroundingParquetStoreTest | — | Verify Parquet cache read/write round-trip for repo grounding chunk records |
| UNIT-REPO-FILE-CHUNKER | RepoFileChunkerTest | com.vinekeepers.workflow.planning.rag.RepoFileChunkerTest | — | Verify repository file chunking and content-hash stability for RAG indexing |
| UNIT-GIT-REPO-BRANCH-RESOLVER | GitRepoBranchResolverTest | com.vinekeepers.workflow.planning.rag.GitRepoBranchResolverTest | — | Verify git branch resolution for repo+branch-scoped RAG cache and Qdrant payload filters |
| UNIT-PLANNING-REPO-GROUNDING-SERVICE | PlanningRepoGroundingServiceTest | com.vinekeepers.workflow.planning.rag.PlanningRepoGroundingServiceTest | — | Verify RAG prep spreads, degraded paths when workspace or deps missing, and successful index/search wiring with mocks |
| UNIT-PREP-PLANNING-REPO-GROUNDING-ACTION | PrepPlanningRepoGroundingActionTest | com.vinekeepers.workflow.actions.PrepPlanningRepoGroundingActionTest | — | Verify prep_planning_repo_grounding guards delegate to service |

