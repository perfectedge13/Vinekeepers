# Nova-wiki-architecture

**Inputs:** Wiki context from wiki_prepare (domains), handoff (user request). Handoff may indicate "major refactor" or "new domain" so architecture should be updated. Credentials from env (WIKIJS_URL, WIKIJS_EMAIL, WIKIJS_PASSWORD or WIKIJS_API_KEY).

**Outputs:** Page `/vinekeepers/architecture` created or updated. Pass/Fail and short description.

## Instructions

1. Read **@.cursor/skills/nova-wiki/page-formats.md** for the `/vinekeepers/architecture` format: Title: Architecture. Body: # Overview, # System context, # Major subsystems, # Runtime flows, # Diagram.
2. **Authenticate** per **@.cursor/skills/nova-wiki/auth.md**: use WIKIJS_API_KEY as Bearer token, or login with `authentication { login(strategy: "local", username: $username, password: $password) { jwt } }` and use the returned **jwt** as `Authorization: Bearer <jwt>`. If credentials unset, return Fail.
3. Gather content from **@specs/specs.yml**, **@README.md**, and loaded registry specs: project summary, external systems (e.g. Polygon, MongoDB), domains (major subsystems), how components interact (runtime flows). Optionally include a Mermaid diagram (e.g. from README or generate from domains/jobs).
4. Create or update the page at path `/vinekeepers/architecture` (or without leading slash per auth.md) with the formatted body. Use GraphQL variables for content. Use markdown; for Diagram section use a Mermaid code block if the wiki editor supports it.
5. Return: Pass (or Fail), and one line e.g. "Updated architecture page."
