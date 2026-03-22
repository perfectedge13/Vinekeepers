# Decisions

- **Async ops:** Playbook and shell runs use a background executor so the Discord gateway stays responsive.
- **sendAs workflow bot:** Progress uses `OutboundDeliveryRouter.sendAs(..., workflowBotId)` where `workflowBotId` comes from `__botId` in session state, not a constant in Java.
- **Thread for status:** Menus stay in the parent channel; long output goes to a `deploy-progress` thread.
