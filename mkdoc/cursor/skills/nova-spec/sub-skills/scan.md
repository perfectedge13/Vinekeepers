# Scan (nova-spec)

## Summary

Enumerates paths (source, specs, cursor, root config); derives structure; builds candidate assets, requirements, and candidate features with suggested_domain.

## Key points

- See **.cursor/skills/nova-spec/sub-skills/scan.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Scan (sub-skill)

**Inputs:** None required. Optional: a path list to restrict scan scope; default = full repository.

**Outputs:** Candidate features (slug, title, requirement_ids, asset_ids, suggested_domain); current spec state (specs.yml + all registry files); candidate assets and candidate requirements (internal handoff).

**References:** **@specs/schema/specs-index.schema.json**, **@specs/schema/req-registry.schema.json**, **@.cursor/skills/common/requirement-tracking.md**, **@.cursor/skills/common/project-config.md**. Use only existing schema keys and the registry's `enums`. Asset and requirement ids must match idToken pattern: `^[A-Z0-9][A-Z0-9\-_.]*$`. **Spec index path:** From **@.cursor/project.yml** `paths.specs_index` if present, else `specs/specs.yml`. **Scan roots:** If project.yml has `scan.root_config_files` and `scan.source_dirs`, use those; else default to pom.xml, README.md, .env.example and src/main/java, src/test/java.

---

## 1.1 Enumerate paths

Scan the repository and list:

- **Source:** From **@.cursor/project.yml** `scan.source_dirs` if present (e.g. `src/main/java`, `src/test/java`), else default `src/main/java`, `src/test/java`; enumerate matching files. Include equivalent for other languages if present.
- **Specs:** Directory containing the spec index (from `paths.specs_index`): `**/*.yml`, and that dir's `schema/**/*.json` if present.
- **Cursor:** `.cursor/rules/**`, `.cursor/skills/**`, `.cursor/workflows/**`.
- **Root/config:** From **@.cursor/project.yml** `scan.root_config_files` if present (e.g. pom.xml, README.md, .env.example), else default those; and other top-level config or entrypoint files.

## 1.2 Derive structure

- **Packages and entrypoints:** From source, list package names and identify main/entry classes (e.g. class with `public static void main` or project's documented entrypoint).
- **Test → production mapping:** For each test file, infer which production class or package it exercises (e.g. `EnvLoaderTest` → `EnvLoader`, `RouterTest` → `Router`).
- **Key config/spec roles:** Which files are the spec index, which are registries, which are schemas.

## 1.3 Build candidate lists (internal handoff)

- **Candidate assets:** For each significant file or logical unit (main classes, key packages, config files, spec files), note: path, suggested role, suggested requirement theme (e.g. "load .env", "route events", "publish events").
- **Candidate requirements:** From code, README, and structure, infer capability themes (e.g. "Application loads .env at startup", "Router matches events to bots", "EventBus publish/subscribe", "Config load bots from YAML", "Tool policy allow/deny"). One theme per requirement.
- **Current spec state:** Read existing spec index (path from project config) and every registry file listed in `specs[].file`. Note existing requirement ids, asset ids, traceability, and validation.tests.

## 1.4 Candidate features from scan

- From **candidate requirements** and **assets** (and existing registry), group by logical area. Use requirement-id prefix (REQ-ENV, REQ-CONFIG, REQ-BOT, REQ-EVENTS, REQ-STATE, REQ-TOOLS, REQ-AUDIT, REQ-REASONER, REQ-WORKFLOW, REQ-CONNECTORS, REQ-CORE) and/or package path (e.g. `com.vinekeepers.env` → env, `com.vinekeepers.config` → config). Each group with at least one requirement and one asset is a **candidate feature** (slug, title, requirement_ids, asset_ids, **suggested_domain**). **suggested_domain:** For requirement ids with prefix REQ-CONNECTORS or assets under package/path containing "connectors", set suggested_domain to **connectors**. For all other prefixes/packages, set suggested_domain to **core** (or the logical area slug that will be merged into core). Do not set all suggested_domains to core. **Finer-grained features:** When a logical area would otherwise produce one umbrella feature but contains **multiple independently identifiable sub-capabilities**, derive **one candidate feature per sub-capability** instead of one. Sub-capabilities are inferable from: (a) **requirement id segments** (e.g. REQ-CONNECTORS-DISCORD vs REQ-CONNECTORS-GITHUB), (b) **asset names or paths** that indicate distinct roles (e.g. DiscordEventSource vs GitHubEventSource), or (c) a **one-requirement-per-asset** pattern where each asset has a clearly distinct responsibility. Use a slug for each sub-capability (e.g. discord, github, or from the requirement/asset name). Each candidate feature gets the appropriate requirement_ids and asset_ids. **Prefer finer-grained features** when the split is clearly inferable; do not collapse distinct sub-capabilities into a single feature. Output: list of candidate features with suggested_domain so the update phase can infer domains and categorize.

## Return

Pass; candidate features (with suggested_domain); current spec state; candidate assets/requirements.
```

</details>


