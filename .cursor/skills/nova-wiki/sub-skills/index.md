# Nova-wiki-index

**Inputs:** Wiki context from wiki_prepare (domains, features), handoff (user request). Credentials: use `WIKIJS_URL`, `WIKIJS_EMAIL`, `WIKIJS_PASSWORD` (or `WIKIJS_API_KEY`) from env; do not hardcode. If not in process env, load from project root .env per **@.cursor/skills/nova-wiki/auth.md**.

**Outputs:** Pages created or updated: &lt;path_prefix&gt;, &lt;path_prefix&gt;/features, &lt;path_prefix&gt;/features/domain, and each &lt;path_prefix&gt;/features/domain/&lt;domain-slug&gt;. Pass/Fail and count of pages updated. **Wiki path prefix:** From **@.cursor/project.yml** `wiki.path_prefix` (e.g. `/vinekeepers` or `Vinekeepers`); if missing, derive from project name (e.g. lowercase slug). **Project name (title):** From project.yml `project_name` or first registry `project.name`. See **@.cursor/skills/common/project-config.md**.

## Instructions

1. Read **@.cursor/skills/nova-wiki/page-formats.md** and use the exact format for each page type you own (root, features, domain index, per-domain index).
2. **Authenticate** per **@.cursor/skills/nova-wiki/auth.md**: if `WIKIJS_API_KEY` is set, use it as Bearer token; otherwise POST to `WIKIJS_URL/graphql` with mutation `authentication { login(strategy: "local", username: $username, password: $password) { jwt } }` (variables: strategy `"local"`, username = WIKIJS_EMAIL, password = WIKIJS_PASSWORD). Use the returned **jwt** (not token) as `Authorization: Bearer <jwt>` on all following requests. If credentials are unset, return Fail with message "WIKIJS credentials not set."
3. For each page in order (use **path_prefix** from project config for all paths):
   - **`<path_prefix>`** — Ensure exists. Title: &lt;project name from config or registry&gt;. Body: # Overview (project summary from README or specs), # Quick links (links to Features, Architecture, Runbooks). Create or update via `pages.create` / `pages.update`.
   - **`<path_prefix>/features`** — Ensure exists. Title: Features. Body: # Definition of a feature (paragraph per page-formats), # Domains (table or list with Domain, Description, Link to each `&lt;path_prefix&gt;/features/domain/&lt;domain-slug&gt;`).
   - **`<path_prefix>/features/domain`** — Ensure exists. Title: Domain index. Body: # Domains (short intro), list of domains with links to `&lt;path_prefix&gt;/features/domain/&lt;domain-slug&gt;`.
   - **`<path_prefix>/features/domain/<domain-slug>`** — For each domain from wiki context, ensure page exists. Title: domain display name. Body: # Features (table or list: Feature, Status, Link to feature summary page for that domain).
4. Use the path strings as the Wiki.js page path (e.g. `path: "<path_prefix>/features"` or without leading slash if the wiki uses that format; see auth.md). For **pages.create** and **pages.update**, pass **content** and other string fields via GraphQL variables to avoid escaping issues. Create parent hierarchy if the API requires it.
5. Return: Pass (or Fail if auth or API failed), and one line e.g. "Updated 4 index pages."
