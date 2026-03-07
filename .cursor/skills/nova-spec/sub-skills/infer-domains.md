# Infer domains (sub-skill)

**Inputs (handoff):** Candidate features (slug, title, requirement_ids, asset_ids, **suggested_domain** from scan 1.4); current spec index (path from project config; specs[].file); list of existing registry file paths (same directory as spec index, e.g. specs/core-registry.yml).

**Outputs:** List of { domain_slug, registry_file, exists }. For each domain: registry_file = `<domain_slug>-registry.yml`; exists = true if that file already exists, false if the caller must create it.

## Logic

1. Collect domain slugs from (a) **existing registry file stems** (e.g. core-registry.yml → core), (b) **suggested_domain** from each candidate feature.
2. **First-class domains:** A fixed set of suggested_domain slugs **always** get their own registry when they have at least one candidate feature. Initially: **connectors**. Every other suggested_domain (env, config, bot, events, state, tools, audit, reasoner, workflow, core) is assigned to the single domain **core**. Build the domain list as: (1) **core** is always included. (2) For each first-class slug (e.g. connectors), if there exists at least one candidate feature with that suggested_domain, add that domain to the list.
3. For each chosen domain_slug, set registry_file = `<domain_slug>-registry.yml` and exists = true if `specs/<domain_slug>-registry.yml` exists on disk (or is in specs[].file), else false.
4. Output the list. If exists is false, the orchestrator will create the registry in update-registry.

**First-class domains:** First-class domain slugs (each gets its own registry when it has ≥1 feature): **connectors**. All other suggested_domains map to core. To add another first-class domain later (e.g. api, persistence), add its slug to this list and ensure scan 1.4 sets suggested_domain for that area accordingly.

## Return

Pass; list of { domain_slug, registry_file, exists }.
