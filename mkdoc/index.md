# Vinekeepers

<img src="img/Vinekeepers.png" alt="Vinekeepers" width="350" />

---

## Overview

**Vinekeepers** is a configurable platform for AI-driven bots. Every bot follows the same core loop: **Listen** (Discord, Git, GitHub, schedules), **Load context** (session and state), **Think** (LLM or rules), **Act** (allowed tools only), **Persist** (state and audit log), and **Respond** (replies, comments, PRs).

Bots are defined as **policies + workflows + tools + memory**, driven by events. You add new bots by YAML configuration—routing, model, persona, workflow, tool allowlist, and guardrails—without changing code. The runtime enforces **"LLM proposes, Vinekeepers disposes"**: every tool call is validated against schema, allowlist, scope, and confirmation rules before execution.

The codebase includes an event bus, config loader, bot definitions, state store, audit logging, tool registry, and connectors (e.g. Discord and GitHub). See [Architecture](architecture.md) for the big picture and [Features](features/index.md) for per-area docs.

---

## Quick links

- [Features](features/index.md)
- [Architecture](architecture.md)
- [Runbooks](runbooks/index.md)
- [Cursor](cursor/index.md)
