# Nova-wiki wiki_prepare output — wiki context for downstream steps

Generated: _(full wiki sync or affected_features from handoff)._

## Wiki context summary

| Field | Value |
|-------|--------|
| **domains_count** | N |
| **features_count** | N |
| **affected_features** | all / list |
| **updates** | _(classification)_ |

## Domains

_(Table: slug, display_name, description.)_

## Features

_(Table: feature_slug, domain_slug, display_name, status.)_

## Affected features

_(List of (domain_slug, feature_slug) or "all.")_

## Updates (classification)

- **full_sync** / **selective** — _(what to update)_
- **sync_domains** — Ensure domain pages exist.
- **sync_features** — Ensure feature pages exist.
- **update_architecture** — Refresh /vinekeepers/architecture from current specs.
- **update_runbooks** — Ensure runbooks exist.
- **update_feature_dossiers** — Create/update feature dossiers.

## Spec index source

- `specs/specs.yml` _(or "not present.")_
