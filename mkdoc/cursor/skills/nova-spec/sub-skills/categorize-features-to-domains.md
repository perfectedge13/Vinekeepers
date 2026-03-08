# Categorize features to domains

## Summary

Maps each candidate feature to exactly one domain; outputs per-domain requirement_ids, asset_ids, features for update-registry.

## Key points

- Exclude candidate features whose slug matches a configured bot id; merge their requirements and assets into capability features instead.
- Assign every remaining feature, requirement, and asset to exactly one domain with no orphans or cross-registry duplicates.
- See **.cursor/skills/nova-spec/sub-skills/categorize-features-to-domains.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Categorize features to domains (sub-skill)

**Inputs (handoff):** Candidate features (slug, title, requirement_ids, asset_ids, suggested_domain); list of domains from infer-domains (domain_slug, registry_file, exists); current registry contents (which requirements, assets, features are in which registry file).

**Outputs:** (1) Mapping feature_slug → domain_slug. (2) Per domain_slug: { requirement_ids[], asset_ids[], features[] } where each feature has id, slug, title, requirement_ids, asset_ids, status. Every requirement and every asset must appear in exactly one domain (no orphans; no duplicate across registries).

## Logic

1. **Exclude bot-id features:** If a candidate feature's slug equals a **known bot id**, do **not** add it to the feature list. Bot ids come from **@.cursor/project.yml** `scan.bots_config` if present (e.g. `config/bots.yaml`), else default `config/bots.yaml` when that path exists; parse and collect `bots[].id`. For each such excluded feature, merge its requirement_ids and asset_ids into the appropriate **capability** features for that domain (bot, workflow, config). Add those requirement_ids and asset_ids to the existing bot, workflow, and/or config feature entries in the per-domain data so they appear in exactly one domain with no orphan requirements/assets. If no bots config is available, skip this step.
2. Assign each remaining candidate feature to **exactly one domain** that appears in the list from infer-domains. If the feature's suggested_domain equals one of the domain_slugs in that list (e.g. connectors), assign the feature to that domain; otherwise assign to **core**. Do not assign a feature to a domain that is not in the infer-domains output. Use requirement-id prefix or package only to resolve suggested_domain when unclear (e.g. REQ-CONNECTORS → connectors, REQ-CORE/REQ-ENV/… → core).
3. For each domain, collect all requirement_ids and asset_ids from the features assigned to that domain (including those merged from excluded bot-id features). Ensure each requirement id and each asset id appears in exactly one domain (if a requirement is needed by features in two domains, assign it to one and reference from the other or keep one copy per domain by convention).
4. Build per-domain feature list: for each feature in that domain, include full feature shape (id FEAT-&lt;UPPERCASE-SLUG&gt;, slug, title, requirement_ids, asset_ids, status) for update-registry.
5. Output the mapping and the per-domain data.

## Return

Pass; feature_slug → domain_slug; per domain_slug: { requirement_ids[], asset_ids[], features[] }.
```

</details>


