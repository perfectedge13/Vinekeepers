# Maintenance

# Procedures

## Keep specs and docs aligned

1. Update the impacted registry requirements, assets, and validation metadata when implementation behavior changes.
2. Run schema and drift validation for the spec set.
3. Re-run the `nova-code` `mk` step so feature dossiers, architecture, runbooks, and Cursor docs stay in sync.

## Review bot and workflow configuration

1. Audit `config/bots.yaml` for stale routing rules, deprecated workflow refs, or incorrect conversation settings.
2. Confirm shared tools registered in `Bootstrap` still match the tool names used by configured workflows and reasoners.
3. Re-run compile and tests after configuration-affecting changes.

## Maintain Cursor docs

1. When rules, skills, or workflows change under `.cursor/`, refresh the `mkdoc/cursor/` pages.
2. Confirm `mkdocs.yml` still includes the Cursor nav section and any new sub-skill pages.

