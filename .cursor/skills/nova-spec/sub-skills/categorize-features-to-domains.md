# Categorize features to domains (sub-skill)

**Inputs (handoff):** Candidate features (slug, title, requirement_ids, asset_ids, suggested_domain); list of domains from infer-domains (domain_slug, registry_file, exists); current registry contents (which requirements, assets, features are in which registry file).

**Outputs:** (1) Mapping feature_slug → domain_slug. (2) Per domain_slug: { requirement_ids[], asset_ids[], features[] } where each feature has id, slug, title, requirement_ids, asset_ids, status. Every requirement and every asset must appear in at least one domain-owned feature, with no orphans.

## Logic

1. **Do not keep bot ids as feature slugs.** If a candidate feature's slug equals a known bot id from the bots config, rename or split it into capability-focused features instead of emitting the bot id directly. Preserve the capability coverage, but avoid a feature slug that is just the bot id.
2. Assign each candidate feature to the best matching domain from infer-domains using the strongest available evidence:
   - an existing `domain_slug` already attached to the feature
   - the candidate `suggested_domain`
   - the dominant package/folder cluster of the feature's assets
   - requirement title/statement keywords only as a last resort
   Do not fall back to a hardcoded umbrella domain.
3. Detect coarse features before final assignment. Split a candidate feature when one or more are true:
   - it spans multiple unrelated package clusters
   - it mixes clearly different responsibilities (for example routing vs policy vs runtime model)
   - it owns many assets or requirements compared with neighboring features
   - its title/summary can only be described as an umbrella capability
   Split along requirement groups, asset path clusters, or behavior boundaries. Keep stable ids/slugs when a current feature still cleanly represents one of the resulting slices.
4. For each domain, collect requirement_ids and asset_ids from the assigned features. A requirement or asset may participate in more than one feature when that improves traceability, but each domain should still have a clear primary ownership slice.
5. Build the per-domain feature list: for each feature in that domain, include full feature shape (id FEAT-&lt;UPPERCASE-SLUG&gt;, slug, title, requirement_ids, asset_ids, status) for update-registry.
6. Output the mapping and the per-domain data, including any splits or renames performed to avoid oversized features.

## Return

Pass; feature_slug → domain_slug; per domain_slug: { requirement_ids[], asset_ids[], features[] }.
