# One-off patch for arrietty_room workflow; run from repo root: python scripts/patch_arrietty_workflow.py
import yaml
from pathlib import Path

def main():
    p = Path("config/bots.yaml")
    d = yaml.safe_load(p.read_text(encoding="utf-8"))
    steps = d["workflows"]["arrietty_room"]["steps"]
    insert_at = 24
    new_block = [
        {"type": "call_action", "action": "synthesize_pre_critique_artifacts"},
        {"type": "call_action", "action": "build_role_planning_thread_messages", "storeSpread": True},
        {
            "type": "call_action",
            "action": "post_channel_message",
            "bind": {
                "content": "{{architectThreadMessage}}",
                "asRole": "architect",
                "target": "thread",
            },
        },
        {
            "type": "call_action",
            "action": "post_channel_message",
            "bind": {
                "content": "{{auditorThreadMessage}}",
                "asRole": "auditor",
                "target": "thread",
            },
        },
        {
            "type": "call_action",
            "action": "post_channel_message",
            "bind": {
                "content": "{{scribeThreadMessage}}",
                "asRole": "scribe",
                "target": "thread",
            },
        },
    ]
    steps[insert_at:insert_at] = new_block
    delta = len(new_block)

    def bump_target(n):
        if not isinstance(n, int):
            return n
        if n < insert_at:
            return n
        if n == insert_at:
            return n
        return n + delta

    def walk(obj):
        if isinstance(obj, dict):
            for k, v in obj.items():
                if k == "next" and isinstance(v, int):
                    obj[k] = bump_target(v)
                else:
                    walk(v)
        elif isinstance(obj, list):
            for x in obj:
                walk(x)

    walk(d["workflows"]["arrietty_room"]["steps"])

    for s in d["workflows"]["arrietty_room"]["steps"]:
        if s.get("type") != "branch":
            continue
        for br in s.get("branches", []):
            wh = br.get("when")
            if not isinstance(wh, dict):
                continue
            if wh.get("key") == "planApprovalDecision" and wh.get("value") in (
                "approve",
                "approve_with_risks",
            ):
                br["next"] = 35

    for s in d["workflows"]["arrietty_room"]["steps"]:
        if s.get("action") == "build_discovery_agenda":
            s["action"] = "build_insight_discovery_agenda"

    steps2 = d["workflows"]["arrietty_room"]["steps"]
    launch_idx = None
    for i, s in enumerate(steps2):
        if s.get("action") == "launch_cursor_run":
            launch_idx = i
            break
    if launch_idx is not None:
        pre = {
            "type": "call_action",
            "action": "post_channel_message",
            "bind": {
                "content": (
                    "**Launching Cursor Cloud** — submitting the run now "
                    "(HTTP timeout up to ~2 minutes). A success or failure "
                    "message follows in this thread."
                ),
                "asRole": "orchestrator",
                "target": "thread",
            },
        }
        steps2.insert(launch_idx, pre)

        def bump_after_launch(obj):
            if isinstance(obj, dict):
                for k, v in obj.items():
                    if k == "next" and isinstance(v, int) and v >= launch_idx:
                        obj[k] = v + 1
                    else:
                        bump_after_launch(v)
            elif isinstance(obj, list):
                for x in obj:
                    bump_after_launch(x)

        bump_after_launch(d["workflows"]["arrietty_room"]["steps"])

    for s in d["workflows"]["arrietty_room"]["steps"]:
        if s.get("type") != "call_action" or s.get("action") != "post_channel_message":
            continue
        b = s.get("bind") or {}
        c = b.get("content", "")
        if isinstance(c, str) and "Discovery kickoff (coordinator)" in c:
            b["content"] = (
                "**Discovery kickoff (coordinator)**\n\n"
                "Repo: `{{project}}`\n"
                "**Request:** {{codeChange}}\n\n"
                "We will draft a full planning packet from your request and repo context. "
                "**Reply once** with scope boundaries, acceptance criteria, constraints, "
                "and risks if you have them — or say **continue** to proceed with drafts. "
                "Follow-ups will be bundled where possible."
            )
        if "prompt" in b and isinstance(b["prompt"], str):
            if "What scope, acceptance criteria" in b["prompt"]:
                b["prompt"] = (
                    "Optional: scope, acceptance criteria, constraints, or risks in one message "
                    "(or **continue** for draft-led planning)."
                )

    p.write_text(
        yaml.dump(d, default_flow_style=False, sort_keys=False, allow_unicode=True, width=1000),
        encoding="utf-8",
    )
    print("OK steps", len(d["workflows"]["arrietty_room"]["steps"]))


if __name__ == "__main__":
    main()
