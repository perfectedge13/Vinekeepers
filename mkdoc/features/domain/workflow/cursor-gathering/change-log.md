# Change log

# Entries

## 2026-03-09

- **Cursor API error visibility:** CursorCloudAdapterImpl constructor masks API key in logs; transport/send logging added for diagnostics without leaking key or body. CursorCloudException message is surfaced to callers (e.g. CursorCloudAdapterImplTest.transportExceptionSurfacesMessage); CursorCloudTransport throws Exception so adapter can wrap and expose cause.
- **Cursor adapter bug-fix:** Robust non-2xx error parsing (multiple response shapes: nested error.message/code, top-level message, plain text, empty body); safe per-request diagnostics at DEBUG (URI, key configured, model, repo, branch); JSON request body uses NON_NULL; CursorCloudAdapterImplTest extended with error-shape and auth tests. Auth tests (Bearer token for getAgent and launchAgent); non-2xx empty JSON object and non-JSON body tests (non2xxEmptyJsonObjectSurfacesGenericMessage, non2xxNonJsonBodySurfacesInException) added.
- **Edit-reprompt and config alignment:** Luna and configured workflow use `ConfigurableWorkflowRunner` and `ConfigurableWorkflowState`; `StepResult.clearKeys` supports edit-reprompt so state keys can be cleared before re-prompting. `config/bots.yaml` and workflow runner/state assets updated for consistency.
- **REQ-LUNA-001 acceptance (confirmation-branch clear):** Confirmation step (Launch / Edit repo / Edit request / Cancel) clears branch/state when the user chooses Edit repo or Edit request so the flow returns to repo or request input without carrying prior confirmation state.
- **Luna discordAuthors:** Luna routing in `config/bots.yaml` can include optional `discordAuthors` (e.g. `novawilde13_72571`) so only listed Discord users can trigger Luna when they mention the bot; gateway and NormalizedEventContext supply author data for routing.

## Prior

- Renamed the documented capability from a Luna-only dossier to the spec-defined `cursor-gathering` feature.
- Captured the configured `luna_cursor` flow, Cursor adapter handoff, and Discord reply path as one feature.

