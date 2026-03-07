# Nova-wiki-architecture

**Inputs:** Wiki context from wiki_prepare (domains), handoff (user request). Handoff may indicate "major refactor" or "new domain" so architecture should be updated. Credentials from env (WIKIJS_URL, WIKIJS_EMAIL, WIKIJS_PASSWORD or WIKIJS_API_KEY). If not in process env, load from project root .env per **@.cursor/skills/nova-wiki/auth.md**.

**Outputs:** Page &lt;path_prefix&gt;/architecture created or updated. Pass/Fail and short description. **Wiki path prefix** from **@.cursor/project.yml** `wiki.path_prefix`; see **@.cursor/skills/common/project-config.md**.

## Instructions

1. Read **@.cursor/skills/nova-wiki/page-formats.md** for the architecture page format: Title: Architecture. Body: # Overview, # System context, # Major subsystems, # Runtime flows, # Diagram. Use **path_prefix** from project config for the path.
2. **Authenticate** per **@.cursor/skills/nova-wiki/auth.md**: use WIKIJS_API_KEY as Bearer token, or login with `authentication { login(strategy: "local", username: $username, password: $password) { jwt } }` and use the returned **jwt** as `Authorization: Bearer <jwt>`. If credentials unset, return Fail.
3. Gather content from **@specs/specs.yml**, **@README.md**, and loaded registry specs: project summary, external systems (e.g. Polygon, MongoDB), domains (major subsystems), how components interact (runtime flows). Optionally include a Mermaid diagram (e.g. from README or generate from domains/jobs).
4. Create or update the page at path &lt;path_prefix&gt;/architecture (path_prefix from **@.cursor/project.yml** `wiki.path_prefix`; with or without leading slash per auth.md) with the formatted body. Use GraphQL variables for content. Use markdown; for Diagram section use a Mermaid code block if the wiki editor supports it.
5. Return: Pass (or Fail), and one line e.g. "Updated architecture page."
