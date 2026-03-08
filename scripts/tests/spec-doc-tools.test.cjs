const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawnSync } = require('child_process');

const REPO_ROOT = path.resolve(__dirname, '..', '..');
const SCRIPTS_DIR = path.join(REPO_ROOT, 'scripts');
const SCHEMA_DIR = path.join(REPO_ROOT, 'specs', 'schema');

function writeFile(filePath, content) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, content);
}

function writeMarkdown(filePath, title) {
  writeFile(filePath, `# ${title}\n\nGenerated for test coverage.\n`);
}

function runScript(scriptName, fixtureRoot) {
  return spawnSync(process.execPath, [path.join(SCRIPTS_DIR, scriptName)], {
    env: { ...process.env, VINEKEEPERS_ROOT: fixtureRoot },
    encoding: 'utf8',
  });
}

function createFixture(options = {}) {
  const {
    omitConfigDomain = false,
    docPathOverride = 'features/domain/config/config.md',
  } = options;
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'vinekeepers-spec-doc-'));

  fs.cpSync(SCHEMA_DIR, path.join(root, 'specs', 'schema'), { recursive: true });

  writeFile(
    path.join(root, '.cursor', 'project.yml'),
    [
      'project_name: Fixture',
      'paths:',
      '  specs_index: specs/specs.yml',
      '  docs_dir: docs-out',
      '  mkdocs_file: mkdocs.yml',
      '',
    ].join('\n')
  );

  writeFile(
    path.join(root, 'specs', 'specs.yml'),
    [
      'scope:',
      '  primary_assets:',
      '    - src/main/java/com/example/App.java',
      '',
      'change_triggers:',
      '  paths:',
      '    - src',
      '    - specs',
      '    - docs-out',
      '    - mkdocs.yml',
      '    - .cursor',
      '',
      'specs:',
      '  - file: core-registry.yml',
      '',
      'domains:',
      '  - slug: env',
      '    spec_file: core-registry.yml',
      ...(omitConfigDomain
        ? []
        : ['  - slug: config', '    spec_file: core-registry.yml']),
      '',
      'interfaces:',
      '  cli:',
      '    - command: mvn test',
      '      description: Run tests',
      '',
      'validation:',
      '  commands:',
      '    test: mvn test',
      '    build_check: mvn compile',
      '  shell: powershell',
      '  entrypoint: src/main/java/com/example/App.java',
      '',
    ].join('\n')
  );

  writeFile(path.join(root, 'src', 'main', 'java', 'com', 'example', 'App.java'), 'class App {}\n');
  writeFile(path.join(root, 'src', 'main', 'java', 'com', 'example', 'EnvLoader.java'), 'class EnvLoader {}\n');
  writeFile(path.join(root, 'src', 'main', 'java', 'com', 'example', 'ConfigLoader.java'), 'class ConfigLoader {}\n');

  writeFile(
    path.join(root, 'specs', 'core-registry.yml'),
    [
      'schema:',
      '  id: req-registry',
      '  version: "1.0.0"',
      '  updated_utc: "2026-03-07T18:45:00Z"',
      '',
      'project:',
      '  id: fixture',
      '  name: Fixture',
      '  description: Fixture repo',
      '  stack:',
      '    language: Java',
      '    runtime: "21"',
      '',
      'enums:',
      '  status: [draft, active, deprecated]',
      '  priority: [low, medium, high]',
      '  type: [functional, non-functional, constraint]',
      '  handling_strategy: [fail, retry, skip]',
      '  asset_kind: [source, config, spec, workflow]',
      '  dependency_kind: [library, tool, service]',
      '  dependency_scope: [build, test, runtime]',
      '  dependency_criticality: [optional, required]',
      '',
      'dependencies:',
      '  items: []',
      '',
      'assets:',
      '  - id: ASSET-ENV-LOADER',
      '    kind: source',
      '    path: src/main/java/com/example/EnvLoader.java',
      '    role: Load environment variables',
      '    requires: [REQ-ENV-001]',
      '    feature_ids: [FEAT-ENV]',
      '  - id: ASSET-CONFIG-LOADER',
      '    kind: source',
      '    path: src/main/java/com/example/ConfigLoader.java',
      '    role: Load config',
      '    requires: [REQ-CONFIG-001]',
      '    feature_ids: [FEAT-CONFIG]',
      '',
      'requirements:',
      '  - id: REQ-ENV-001',
      '    title: Load env values',
      '    statement: Load env values before startup.',
      '    status: active',
      '    priority: medium',
      '    type: functional',
      '    behavior:',
      '      inputs: []',
      '      outputs: []',
      '      rules: []',
      '      defaults: []',
      '      errors: []',
      '    acceptance:',
      '      criteria: []',
      '      examples: []',
      '    traceability:',
      '      assets: [ASSET-ENV-LOADER]',
      '      symbols: []',
      '    validation:',
      '      tests: []',
      '  - id: REQ-CONFIG-001',
      '    title: Load config values',
      '    statement: Load config values before startup.',
      '    status: active',
      '    priority: medium',
      '    type: functional',
      '    behavior:',
      '      inputs: []',
      '      outputs: []',
      '      rules: []',
      '      defaults: []',
      '      errors: []',
      '    acceptance:',
      '      criteria: []',
      '      examples: []',
      '    traceability:',
      '      assets: [ASSET-CONFIG-LOADER]',
      '      symbols: []',
      '    validation:',
      '      tests: []',
      '',
      'features:',
      '  - id: FEAT-ENV',
      '    slug: env',
      '    title: Env feature',
      '    requirement_ids: [REQ-ENV-001]',
      '    asset_ids: [ASSET-ENV-LOADER]',
      '    status: active',
      '    domain_slug: env',
      '    doc_path: features/domain/env/env.md',
      '    summary: Env summary.',
      '  - id: FEAT-CONFIG',
      '    slug: config',
      '    title: Config feature',
      '    requirement_ids: [REQ-CONFIG-001]',
      '    asset_ids: [ASSET-CONFIG-LOADER]',
      '    status: active',
      '    domain_slug: config',
      `    doc_path: ${docPathOverride}`,
      '    summary: Config summary.',
      '',
    ].join('\n')
  );

  writeMarkdown(path.join(root, 'docs-out', 'index.md'), 'Fixture');
  writeMarkdown(path.join(root, 'docs-out', 'features', 'index.md'), 'Features');
  writeMarkdown(path.join(root, 'docs-out', 'features', 'domain.md'), 'Domain index');
  writeMarkdown(path.join(root, 'docs-out', 'features', 'domain', 'env.md'), 'Env');
  writeMarkdown(path.join(root, 'docs-out', 'features', 'domain', 'config.md'), 'Config');
  writeMarkdown(path.join(root, 'docs-out', 'features', 'domain', 'env', 'env.md'), 'Env feature');
  writeMarkdown(path.join(root, 'docs-out', 'features', 'domain', 'config', 'config.md'), 'Config feature');

  for (const featureSlug of ['env', 'config']) {
    const baseDir = path.join(root, 'docs-out', 'features', 'domain', featureSlug, featureSlug);
    for (const subpage of [
      'how-it-works.md',
      'change-log.md',
      'known-issues.md',
      'decisions.md',
      'contracts.md',
      'tests.md',
      'diagrams.md',
    ]) {
      writeMarkdown(path.join(baseDir, subpage), `${featureSlug} ${subpage}`);
    }
  }

  writeFile(
    path.join(root, 'mkdocs.yml'),
    [
      'site_name: Fixture',
      'docs_dir: docs-out',
      'nav:',
      '  - Home: index.md',
      '  - Features:',
      '      - Overview: features/index.md',
      '      - Domains:',
      '          - Domain index: features/domain.md',
      '          - Env:',
      '              - Overview: features/domain/env.md',
      '              - Env feature: features/domain/env/env.md',
      '          - Config:',
      '              - Overview: features/domain/config.md',
      `              - Config feature: ${docPathOverride}`,
      '',
    ].join('\n')
  );

  return root;
}

test('validators accept docs_dir-aware multi-domain registry fixtures', () => {
  const fixture = createFixture();
  const validateSpecs = runScript('validate-specs.cjs', fixture);
  const validateDrift = runScript('validate-drift.cjs', fixture);
  const validateDocs = runScript('validate-docs.cjs', fixture);

  assert.equal(validateSpecs.status, 0, validateSpecs.stderr || validateSpecs.stdout);
  assert.equal(validateDrift.status, 0, validateDrift.stderr || validateDrift.stdout);
  assert.equal(validateDocs.status, 0, validateDocs.stderr || validateDocs.stdout);
});

test('validate-drift rejects features whose domain_slug is missing from specs.yml domains[]', () => {
  const fixture = createFixture({ omitConfigDomain: true });
  const result = runScript('validate-drift.cjs', fixture);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /uses undeclared domain_slug/i);
});

test('validate-docs rejects unsafe feature doc_path overrides', () => {
  const fixture = createFixture({ docPathOverride: '../outside.md' });
  const result = runScript('validate-docs.cjs', fixture);

  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /invalid feature doc_path/i);
});
