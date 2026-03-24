# Infer domains

## Summary

Infers domain slugs from existing feature metadata, candidate feature suggestions, and asset/package clusters, then maps each domain to the registry file that currently owns it or to a proposed `<domain>-registry.yml`.

## Key points

- Preserve existing `features[].domain_slug` ownership when repo evidence still supports it.
- Infer domains from multiple signals instead of relying on a fixed first-class-domain list.
- Return `{ domain_slug, registry_file, exists, source }` so later nova-spec steps can build the authoritative registry layout.

## Source (markdown)

<details class="skill-source-wrap">
<summary>Click to expand</summary>

```markdown
# Infer domains (sub-skill)

**Inputs (handoff):** Candidate features (slug, title, requirement_ids, asset_ids, **suggested_domain** from scan 1.4); current spec index (path from project config; specs[].file); list of existing registry file paths (same directory as spec index, e.g. specs/core-registry.yml).

**Outputs:** List of { domain_slug, registry_file, exists, source }. For each domain: registry_file = the registry that currently owns the domain when known; otherwise `<domain_slug>-registry.yml`. `exists = true` if that registry already exists, false if the caller must create it.

## Logic

1. Collect candidate domain slugs from multiple signals, in priority order:
   - existing `features[].domain_slug` values across loaded registries
   - each candidate feature's `suggested_domain`
   - top-level package or folder clusters from candidate asset paths (for example `src/main/java/.../<package>/...` or `src/<area>/...`)
   - existing registry file stems as a fallback only
2. Normalize each candidate domain slug to a stable lowercase slug and de-duplicate.
3. **Do not use a hardcoded first-class domain list.** A domain should exist when the current repo evidence supports it. Prefer preserving an existing `features[].domain_slug` when it still matches the asset/requirement cluster.
4. Detect domains that are too broad or mixed:
   - the domain would own multiple unrelated top-level package clusters
   - the domain would own many features with weak overlap in requirements/assets
   - a single umbrella domain is only present because of historical registry layout
   When that happens, keep the broader domain only if the evidence really points to a shared runtime capability; otherwise emit the narrower inferred domains instead.
5. For each chosen domain slug, resolve its registry file:
   - if an existing registry already contains features with that `domain_slug`, prefer that registry file
   - else if a registry stem equals the domain slug, use that registry
   - else propose `<domain_slug>-registry.yml`
6. Output the list including `source` (for example `existing-feature-domain`, `suggested-domain`, `asset-path-cluster`, `registry-stem`). If `exists` is false, the orchestrator may create the registry in update-registry.

## Return

Pass; list of { domain_slug, registry_file, exists, source }.
```

</details>


