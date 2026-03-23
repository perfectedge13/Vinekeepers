#!/usr/bin/env python3
"""
Remove dead / wrong intake scaffolding from arrietty_room_legacy:
- Steps 81-86: optional solicitation mini-loop (prompt_for_field Add scope...).
- Steps 36-38: unreachable planningClarification present_choices path (always skipped to plain text).

Rewrites all branch `next` indices after deletions. Fixes:
- Discovery branch when no prompt: else next 28 (was 1).
- Readiness proceed: jump to acknowledge_readiness_human_decision (not human-review repost).
"""
from __future__ import annotations

import copy
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("PyYAML required: pip install pyyaml", file=sys.stderr)
    sys.exit(1)

WORKFLOW_ID = "arrietty_room_legacy"
# Remove higher indices first (81-86), then 36-38 in the *original* list.
REMOVE_RANGES_DESC = [(81, 87), (36, 39)]


def main() -> None:
    root_path = Path(__file__).resolve().parents[1] / "config" / "bots.yaml"
    text = root_path.read_text(encoding="utf-8")
    root = yaml.safe_load(text)
    workflows = root.get("workflows") or {}
    wf = workflows.get(WORKFLOW_ID)
    if not wf or "steps" not in wf:
        raise SystemExit(f"Missing {WORKFLOW_ID}.steps in {root_path}")
    steps: list = wf["steps"]

    # Find target indices by action/content before mutation
    ack_idx = None
    human_review_idx = None
    for i, s in enumerate(steps):
        if s.get("type") == "call_action" and s.get("action") == "acknowledge_readiness_human_decision":
            ack_idx = i
        if s.get("type") == "call_action" and s.get("action") == "post_channel_message":
            bind = s.get("bind") or {}
            c = bind.get("content") or ""
            if "Human review requested" in str(c):
                human_review_idx = i

    if ack_idx is None:
        raise SystemExit("Could not find acknowledge_readiness_human_decision step")
    if human_review_idx is None:
        raise SystemExit("Could not find Human review requested post step")

    def retarget_solicitation_block(obj: dict | list | None) -> None:
        """Branches that pointed at the removed solicitation loop (81-86) must hit human review (87)."""
        if isinstance(obj, dict):
            if "branches" in obj and isinstance(obj["branches"], list):
                for br in obj["branches"]:
                    if isinstance(br, dict):
                        nxt = br.get("next")
                        if isinstance(nxt, int) and 81 <= nxt <= 86:
                            br["next"] = 87
            for v in obj.values():
                retarget_solicitation_block(v)
        elif isinstance(obj, list):
            for item in obj:
                retarget_solicitation_block(item)

    retarget_solicitation_block({"steps": steps})

    def map_index(old: int) -> int:
        n = old
        for start, end in sorted(REMOVE_RANGES_DESC, key=lambda r: r[0], reverse=True):
            if start <= old < end:
                raise ValueError(f"branch target {old} falls inside removed range [{start},{end})")
            if old >= end:
                n -= end - start
        return n

    # Build new steps
    remove_set: set[int] = set()
    for start, end in REMOVE_RANGES_DESC:
        remove_set.update(range(start, end))
    new_steps = [s for i, s in enumerate(steps) if i not in remove_set]

    new_ack = map_index(ack_idx)
    new_human = map_index(human_review_idx)

    def fix_branches(obj: dict | list | None) -> None:
        if isinstance(obj, dict):
            if "branches" in obj and isinstance(obj["branches"], list):
                for br in obj["branches"]:
                    if not isinstance(br, dict):
                        continue
                    nxt = br.get("next")
                    if isinstance(nxt, int):
                        br["next"] = map_index(nxt)
            for v in obj.values():
                fix_branches(v)
        elif isinstance(obj, list):
            for item in obj:
                fix_branches(item)

    wf["steps"] = new_steps
    fix_branches({"steps": new_steps})

    # Post-pass: fix discovery agenda branch (else -> 28 not 1)
    for i, s in enumerate(new_steps):
        if s.get("type") != "branch":
            continue
        branches = s.get("branches") or []
        # Heuristic: branch immediately after build_insight_discovery_agenda
        if i > 0 and new_steps[i - 1].get("action") == "build_insight_discovery_agenda":
            for br in branches:
                if br.get("when") == "else" and br.get("next") == 1:
                    br["next"] = 28

    # Readiness: proceed -> acknowledge (not human-review post)
    for i, s in enumerate(new_steps):
        if s.get("type") != "branch":
            continue
        prev = new_steps[i - 1] if i > 0 else {}
        if prev.get("type") != "capture_field":
            continue
        p2 = new_steps[i - 2] if i > 1 else {}
        if p2.get("type") != "prompt_for_field":
            continue
        pr = str(p2.get("prompt") or "")
        if "Pick what happens next" not in pr and "Readiness needs your decision" not in pr:
            continue
        for br in s.get("branches") or []:
            if not isinstance(br, dict):
                continue
            if br.get("when") == "else":
                continue
            w = br.get("when")
            if isinstance(w, dict) and w.get("key") == "readinessProceedRaw" and w.get("value") == "proceed":
                br["next"] = new_ack

    yaml_dump = yaml.dump(
        root,
        default_flow_style=False,
        allow_unicode=True,
        sort_keys=False,
        width=120,
    )
    root_path.write_text(yaml_dump, encoding="utf-8")
    print(f"Wrote {root_path} ({len(new_steps)} steps)")
    print(f"  acknowledge_readiness_human_decision now at {new_ack} (was {ack_idx})")
    print(f"  human review post now at {new_human} (was {human_review_idx})")


if __name__ == "__main__":
    main()
