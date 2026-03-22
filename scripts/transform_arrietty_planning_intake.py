"""One-off style transform: collapse coordinator kickoff menu, fix branch indices, plain clarification."""
from copy import deepcopy

import yaml


def adjust_next(n: int) -> int:
    if n == 86:
        return 80
    if 80 <= n <= 85:
        return 79
    if n >= 87:
        return n - 6
    return n


def fix_branches(obj):
    if isinstance(obj, dict):
        if "next" in obj:
            v = obj["next"]
            if isinstance(v, int):
                obj["next"] = adjust_next(v)
        for v in obj.values():
            fix_branches(v)
    elif isinstance(obj, list):
        for item in obj:
            fix_branches(item)


def main():
    path = "config/bots.yaml"
    with open(path, encoding="utf-8") as f:
        d = yaml.safe_load(f)

    steps = d["workflows"]["arrietty_room_legacy"]["steps"]
    bootstrap = {
        "type": "call_action",
        "action": "coordinator_intake_bootstrap",
        "storeSpread": True,
    }
    new_steps = steps[:79] + [bootstrap, deepcopy(steps[86])] + steps[87:]
    for s in new_steps:
        fix_branches(s)

    # Plain-text-only clarification path (skip structured choice branch)
    new_steps[35] = {"type": "branch", "branches": [{"when": "else", "next": 39}]}

    # Draft-blocked recovery: another planning round, not coordinator kickoff loop
    for i, s in enumerate(new_steps):
        if s.get("type") != "call_action":
            continue
        bind = s.get("bind")
        if not isinstance(bind, dict):
            continue
        content = str(bind.get("content", ""))
        if "Draft blocked" in content and "packet not posted" in content:
            nxt = new_steps[i + 1] if i + 1 < len(new_steps) else None
            if isinstance(nxt, dict) and nxt.get("type") == "branch":
                for b in nxt.get("branches", []):
                    if b.get("when") == "else" and isinstance(b.get("next"), int):
                        b["next"] = 29
                # User-facing copy: no coordinator menu
                bind["content"] = (
                    "**Draft blocked — packet not posted** — {{planningRoomCycleError}}\n\n\n\n"
                    "{{planningPacketDepthReason}}\n\n\n\n"
                    "Retry depth: **planningPacketDepthRetryRecommended** = "
                    "{{planningPacketDepthRetryRecommended}}. "
                    "Reply in this thread with more detail, constraints, or risks in **plain text** "
                    "and I will run another planning pass — no buttons or commands required."
                )
            break

    d["workflows"]["arrietty_room_legacy"]["steps"] = new_steps

    with open(path, "w", encoding="utf-8") as f:
        yaml.dump(
            d,
            f,
            default_flow_style=False,
            sort_keys=False,
            allow_unicode=True,
            width=100,
        )


if __name__ == "__main__":
    main()
