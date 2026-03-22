"""Replace arrietty_room steps 28-38 with planning room cycle + clarification + packet + critique + gate."""
import copy
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
YAML_PATH = ROOT / "config" / "bots.yaml"

START = 28
OLD_LEN = 11
THRESHOLD = START + OLD_LEN  # old index 39 (first step after removed block)

NEW_BLOCK = [
    {"type": "call_action", "action": "mark_intake_discovery_complete", "storeSpread": True},
    {"type": "call_action", "action": "execute_planning_room_cycle", "storeSpread": True},
    {
        "type": "branch",
        "branches": [
            {"when": {"key": "planningUserInputRequired", "value": "true"}, "next": 31},
            {"when": "else", "next": 36},
        ],
    },
    {
        "type": "call_action",
        "action": "post_channel_message",
        "bind": {
            "content": "{{planningOrchestratorRoundSummary}}",
            "asRole": "orchestrator",
            "target": "thread",
        },
    },
    {
        "type": "prompt_for_field",
        "prompt": "{{planningOrchestratorRoundSummary}}",
        "storeIn": "planningClarificationRaw",
        "intent": "present_choices",
        "choiceProvider": "planningClarification",
    },
    {"type": "capture_field", "storeIn": "planningClarificationRaw"},
    {"type": "call_action", "action": "merge_planning_clarification_choice", "storeSpread": True},
    {"type": "branch", "branches": [{"when": "else", "next": 29}]},
    {
        "type": "branch",
        "branches": [
            {"when": {"key": "planningReadyToPostPacket", "value": "true"}, "next": 37},
            {"when": "else", "next": 42},
        ],
    },
    {"type": "call_action", "action": "post_planning_packet_thread", "storeSpread": True},
    {"type": "call_action", "action": "evaluate_planning_packet_depth", "storeSpread": True},
    {"type": "call_action", "action": "run_plan_critique_and_readiness", "storeSpread": True},
    {"type": "call_action", "action": "evaluate_planning_approval_gate", "storeSpread": True},
    {
        "type": "branch",
        "branches": [
            {"when": {"key": "planReadinessStatus", "value": "BLOCKED"}, "next": 54},
            {
                "when": {"key": "planReadinessStatus", "value": "NEEDS_REVISION"},
                "next": 29,
                "clear": [
                    "discoveryAnswerRaw",
                    "planApprovalDecision",
                    "planningProposalsJson",
                    "planningConfirmQueueJson",
                    "proposalConfirmPrompt",
                    "proposalConfirmProposalId",
                    "proposalConfirmArtifactId",
                    "proposalConfirmSectionId",
                    "proposalConfirmFieldId",
                    "proposalConfirmProposedValue",
                    "planningHasConfirmPending",
                    "readinessProceedRaw",
                    "discoveryBundledApplyJson",
                    "intakeChoice",
                    "planningClarificationRaw",
                    "planningPacketPostedVersion",
                ],
            },
            {"when": {"key": "planReadinessStatus", "value": "NEEDS_HUMAN_DECISION"}, "next": 77},
            {"when": "else", "next": 44},
        ],
    },
    {
        "type": "call_action",
        "action": "post_channel_message",
        "bind": {
            "content": "**Planning packet not ready yet** — {{planningRoomCycleError}}\n\n"
            "{{planningPacketDepthReason}}\n\n"
            "The coordinator menu will offer **Continue to planning packet** to retry, or add a brief note in-thread.",
            "asRole": "orchestrator",
            "target": "thread",
        },
    },
    {"type": "branch", "branches": [{"when": "else", "next": 66}]},
]

NEW_LEN = len(NEW_BLOCK)
DELTA = NEW_LEN - OLD_LEN


def shift_branches(steps: list) -> None:
    """Shift next targets for branches outside the inserted block that pointed past the splice."""
    end_new = START + NEW_LEN
    for si, step in enumerate(steps):
        if step.get("type") != "branch":
            continue
        if START <= si < end_new:
            continue
        for br in step.get("branches", []):
            n = br.get("next")
            if isinstance(n, int) and n >= THRESHOLD:
                br["next"] = n + DELTA


def main() -> None:
    root = yaml.safe_load(YAML_PATH.read_text(encoding="utf-8"))
    steps = root["workflows"]["arrietty_room"]["steps"]
    del steps[START : START + OLD_LEN]
    for i, step in enumerate(NEW_BLOCK):
        steps.insert(START + i, copy.deepcopy(step))
    shift_branches(steps)
    YAML_PATH.write_text(
        yaml.dump(root, default_flow_style=False, sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    print("Patched", YAML_PATH, "steps:", len(steps), "NEW_LEN", NEW_LEN, "DELTA", DELTA)


if __name__ == "__main__":
    main()
