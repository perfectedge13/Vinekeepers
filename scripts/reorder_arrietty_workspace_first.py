"""Move ensure_repo_workspace block to immediately after hydrate for arrietty_room; remap branch next indices."""
from __future__ import annotations

from pathlib import Path

import yaml


def old_to_new(k: int) -> int:
    if k == 0:
        return 0
    if 1 <= k <= 23:
        return k + 3
    if 24 <= k <= 26:
        return k - 23
    return k


def remap_branches(obj: object) -> None:
    if isinstance(obj, dict):
        if obj.get("type") == "branch":
            for b in obj.get("branches") or []:
                n = b.get("next")
                if isinstance(n, int):
                    b["next"] = old_to_new(n)
        for v in obj.values():
            remap_branches(v)
    elif isinstance(obj, list):
        for e in obj:
            remap_branches(e)


def main() -> None:
    path = Path(__file__).resolve().parent.parent / "config" / "bots.yaml"
    text = path.read_text(encoding="utf-8")
    root = yaml.safe_load(text)
    steps = root["workflows"]["arrietty_room"]["steps"]
    block = steps[24:27]
    new_steps = [steps[0]] + block + steps[1:24] + steps[27:]
    root["workflows"]["arrietty_room"]["steps"] = new_steps
    remap_branches(root["workflows"]["arrietty_room"])

    # Swap evaluate_planning_packet_depth before post_planning_packet_thread
    actions = [s.get("action") for s in new_steps]
    try:
        i_post = actions.index("post_planning_packet_thread")
        i_eval = actions.index("evaluate_planning_packet_depth")
    except ValueError:
        path.write_text(
            yaml.dump(root, default_flow_style=False, allow_unicode=True, sort_keys=False, width=120),
            encoding="utf-8",
        )
        return
    if i_post < i_eval:
        new_steps[i_post], new_steps[i_eval] = new_steps[i_eval], new_steps[i_post]

    path.write_text(
        yaml.dump(root, default_flow_style=False, allow_unicode=True, sort_keys=False, width=120),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
