# Wiki.js authentication (for nova-wiki sub-skills)

Use this to obtain a Bearer token for Wiki.js GraphQL requests. All sub-skills that create/update pages must authenticate first.

## Credentials (from env)

- **WIKIJS_URL** — Base URL of the wiki (e.g. `http://192.168.4.34:3080`). No trailing slash.
- **WIKIJS_API_KEY** — If set, use it directly as the Bearer token; skip login.
- **WIKIJS_EMAIL** and **WIKIJS_PASSWORD** — For local strategy login when API key is not set.

## Login (when not using API key)

1. POST to `WIKIJS_URL/graphql` with `Content-Type: application/json`.
2. Body (use variables; do not put password in the query string):

   ```json
   {
     "query": "mutation Login($strategy: String!, $username: String!, $password: String!) { authentication { login(strategy: $strategy, username: $username, password: $password) { jwt } } }",
     "variables": {
       "strategy": "local",
       "username": "<WIKIJS_EMAIL value>",
       "password": "<WIKIJS_PASSWORD value>"
     }
   }
   ```

3. The response field is **jwt** (not `token`). Use that string as the Bearer token.
4. For all subsequent requests (pages.list, pages.single, pages.create, pages.update), send header: `Authorization: Bearer <jwt>`.

## Path format

Wiki.js may use paths with or without a leading slash (e.g. `Vinekeepers` or `Vinekeepers/Features`). If existing pages in the wiki use no leading slash, use the same format when creating or updating so paths match.

## Create/update

- **pages.create** — Requires `path`, `title`, `content`, `description`, `editor` (e.g. `"markdown"`), `isPublished`, `isPrivate`, `locale`, `tags`. Use **variables** for `content` to avoid escaping issues.
- **pages.update** — Requires page `id` (from pages.list or pages.single), plus the same fields as create. Use variables for `content`.
- Reference: **scripts/wikijs-edit-one.cjs** in this repo for a working auth + list + get + update flow.
