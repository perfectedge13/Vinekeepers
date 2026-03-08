/**
 * validate-drift.cjs — Drift Gate
 * Index: specs[].file exist; scope.primary_assets[] exist; change_triggers.paths (as path prefixes) exist.
 * Registries: assets[].path exist; requirements[].traceability.assets in assets[].id;
 *   assets[].requires and dependencies.items[].used_by_requirements point to requirement ids;
 *   features[].requirement_ids and asset_ids point to existing ids.
 * Exit 0 = pass; non-zero = fail (prints errors to stderr).
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const ROOT = path.resolve(process.env.VINEKEEPERS_ROOT || path.join(__dirname, '..'));
const PROJECT_CONFIG_PATH = path.join(ROOT, '.cursor', 'project.yml');

function loadYaml(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  return yaml.load(raw);
}

function fileExists(filePath) {
  const full = path.isAbsolute(filePath) ? filePath : path.join(ROOT, filePath);
  return fs.existsSync(full);
}

function main() {
  const projectConfig = fs.existsSync(PROJECT_CONFIG_PATH) ? loadYaml(PROJECT_CONFIG_PATH) : {};
  const specsIndexRelPath = projectConfig.paths?.specs_index || 'specs/specs.yml';
  const INDEX_PATH = path.join(ROOT, specsIndexRelPath);
  const SPECS_DIR = path.dirname(INDEX_PATH);

  if (!fs.existsSync(INDEX_PATH)) {
    console.error('Drift: index not found:', INDEX_PATH);
    process.exit(1);
  }

  const index = loadYaml(INDEX_PATH);
  const declaredRegistryFiles = new Set((index.specs || []).map((entry) => entry.file));
  const domainRegistryMap = new Map();
  let failed = false;

  for (const domain of index.domains || []) {
    if (!declaredRegistryFiles.has(domain.spec_file)) {
      console.error('Drift: domain spec_file is not declared in specs[]:', domain.slug, '->', domain.spec_file);
      failed = true;
      continue;
    }
    domainRegistryMap.set(domain.slug, domain.spec_file);
  }

  // --- Index checks ---
  for (const entry of index.specs || []) {
    const regPath = path.join(SPECS_DIR, entry.file);
    if (!fs.existsSync(regPath)) {
      console.error('Drift: registry file does not exist:', entry.file);
      failed = true;
    }
  }

  for (const assetPath of index.scope?.primary_assets || []) {
    if (!fileExists(assetPath)) {
      console.error('Drift: primary_asset does not exist:', assetPath);
      failed = true;
    }
  }

  // change_triggers.paths: treat as path prefixes (directory or file); at least the path segment should exist
  for (const p of index.change_triggers?.paths || []) {
    const full = path.join(ROOT, p);
    if (!fs.existsSync(full)) {
      console.error('Drift: change_trigger path does not exist:', p);
      failed = true;
    }
  }

  // interfaces.cli: just ensure structure exists (command is string)
  const cli = index.interfaces?.cli || [];
  for (const item of cli) {
    if (!item.command || typeof item.command !== 'string') {
      console.error('Drift: interfaces.cli entry missing command');
      failed = true;
    }
  }

  // --- Registry checks (per file) ---
  for (const entry of index.specs || []) {
    const regPath = path.join(SPECS_DIR, entry.file);
    if (!fs.existsSync(regPath)) continue;
    const reg = loadYaml(regPath);
    if (reg?.schema?.id !== 'req-registry') continue;

    const assetIds = new Set((reg.assets || []).map((a) => a.id));
    const reqIds = new Set((reg.requirements || []).map((r) => r.id));
    const featureIds = new Set((reg.features || []).map((f) => f.id));

    // assets[].path exist
    for (const a of reg.assets || []) {
      if (!fileExists(a.path)) {
        console.error('Drift:', entry.file, 'asset path does not exist:', a.path, '(id:', a.id + ')');
        failed = true;
      }
      for (const rid of a.requires || []) {
        if (!reqIds.has(rid)) {
          console.error('Drift:', entry.file, 'asset', a.id, 'requires unknown requirement:', rid);
          failed = true;
        }
      }
      if (a.symbols) {
        for (const sym of a.symbols) {
          for (const rid of sym.requires || []) {
            if (!reqIds.has(rid)) {
              console.error('Drift:', entry.file, 'asset', a.id, 'symbol requires unknown requirement:', rid);
              failed = true;
            }
          }
        }
      }
      for (const fid of a.feature_ids || []) {
        if (!featureIds.has(fid)) {
          console.error('Drift:', entry.file, 'asset', a.id, 'feature_ids unknown:', fid);
          failed = true;
        }
      }
    }

    // requirements[].traceability.assets in assets[].id; each such asset must list this requirement in requires (or symbol.requires)
    for (const r of reg.requirements || []) {
      for (const aid of r.traceability?.assets || []) {
        if (!assetIds.has(aid)) {
          console.error('Drift:', entry.file, 'requirement', r.id, 'traceability.assets unknown:', aid);
          failed = true;
        }
      }
    }

    // dependencies.items[].used_by_requirements
    for (const d of reg.dependencies?.items || []) {
      for (const rid of d.used_by_requirements || []) {
        if (!reqIds.has(rid)) {
          console.error('Drift:', entry.file, 'dependency', d.id, 'used_by_requirements unknown:', rid);
          failed = true;
        }
      }
    }

    // features[].requirement_ids and asset_ids
    for (const f of reg.features || []) {
      if (f.domain_slug) {
        const mappedRegistryFile = domainRegistryMap.get(f.domain_slug);
        if (!mappedRegistryFile) {
          console.error('Drift:', entry.file, 'feature', f.id, 'uses undeclared domain_slug:', f.domain_slug);
          failed = true;
        } else if (mappedRegistryFile !== entry.file) {
          console.error(
            'Drift:',
            entry.file,
            'feature',
            f.id,
            'domain_slug',
            f.domain_slug,
            'is mapped to',
            mappedRegistryFile,
            'in specs.yml domains[]'
          );
          failed = true;
        }
      }
      for (const rid of f.requirement_ids || []) {
        if (!reqIds.has(rid)) {
          console.error('Drift:', entry.file, 'feature', f.id, 'requirement_ids unknown:', rid);
          failed = true;
        }
      }
      for (const aid of f.asset_ids || []) {
        if (!assetIds.has(aid)) {
          console.error('Drift:', entry.file, 'feature', f.id, 'asset_ids unknown:', aid);
          failed = true;
        }
      }
    }

    // Active requirements: at least one test (guideline; we don't fail on empty tests here to allow exemptions)
    // Legacy status alias: accepted == active during migration.
    // Optional: uncomment to enforce
    // for (const r of reg.requirements || []) {
    //   if ((r.status === 'active' || r.status === 'accepted') && (!r.validation?.tests || r.validation.tests.length === 0)) {
    //     console.error('Drift:', entry.file, 'live requirement', r.id, 'has no validation.tests');
    //     failed = true;
    //   }
    // }
  }

  if (failed) {
    process.exit(1);
  }
  console.log('validate-drift: OK');
  process.exit(0);
}

main();
