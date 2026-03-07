# Categorize features to domains

## Summary

Maps each candidate feature to exactly one domain; outputs per-domain requirement_ids, asset_ids, features for update-registry.

## Key points

- See **.cursor/skills/nova-spec/sub-skills/categorize-features-to-domains.md** for full instructions.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Categorize features to domains (sub-skill)

**Inputs (handoff):** Candidate features (slug, title, requirement_ids, asset_ids, suggested_domain); list of domains from infer-domains (domain_slug, registry_file, exists); current registry contents (which requirements, assets, features are in which registry file).

**Outputs:** (1) Mapping feature_slug → domain_slug. (2) Per domain_slug: { requirement_ids[], asset_ids[], features[] } where each feature has id, slug, title, requirement_ids, asset_ids, status. Every requirement and every asset must appear in exactly one domain (no orphans; no duplicate across registries).

## Logic

1. Assign each candidate feature to **exactly one domain** that appears in the list from infer-domains. If the feature's suggested_domain equals one of the domain_slugs in that list (e.g. connectors), assign the feature to that domain; otherwise assign to **core**. Do not assign a feature to a domain that is not in the infer-domains output. Use requirement-id prefix or package only to resolve suggested_domain when unclear (e.g. REQ-CONNECTORS → connectors, REQ-CORE/REQ-ENV/… → core).
2. For each domain, collect all requirement_ids and asset_ids from the features assigned to that domain. Ensure each requirement id and each asset id appears in exactly one domain (if a requirement is needed by features in two domains, assign it to one and reference from the other or keep one copy per domain by convention).
3. Build per-domain feature list: for each feature in that domain, include full feature shape (id FEAT-&lt;UPPERCASE-SLUG&gt;, slug, title, requirement_ids, asset_ids, status) for update-registry.
4. Output the mapping and the per-domain data.

## Return

Pass; feature_slug → domain_slug; per domain_slug: { requirement_ids[], asset_ids[], features[] }.
```

</details>
