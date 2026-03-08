/**
 * validate-specs.cjs — Schema Gate
 * Validates specs/specs.yml against specs/schema/specs-index.schema.json
 * and each registry (specs[].file) with schema.id "req-registry" against specs/schema/req-registry.schema.json.
 * Exit 0 = pass; non-zero = fail (prints errors to stderr).
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');
const Ajv = require('ajv');

const ROOT = path.resolve(process.env.VINEKEEPERS_ROOT || path.join(__dirname, '..'));
const PROJECT_CONFIG_PATH = path.join(ROOT, '.cursor', 'project.yml');

function loadYaml(filePath) {
  const raw = fs.readFileSync(filePath, 'utf8');
  return yaml.load(raw);
}

function main() {
  const projectConfig = fs.existsSync(PROJECT_CONFIG_PATH) ? loadYaml(PROJECT_CONFIG_PATH) : {};
  const specsIndexRelPath = projectConfig.paths?.specs_index || 'specs/specs.yml';
  const INDEX_PATH = path.join(ROOT, specsIndexRelPath);
  const SPECS_DIR = path.dirname(INDEX_PATH);
  const INDEX_SCHEMA_PATH = path.join(SPECS_DIR, 'schema', 'specs-index.schema.json');
  const REGISTRY_SCHEMA_PATH = path.join(SPECS_DIR, 'schema', 'req-registry.schema.json');

  if (!fs.existsSync(INDEX_SCHEMA_PATH) || !fs.existsSync(REGISTRY_SCHEMA_PATH)) {
    console.error('Schema Gate skipped: specs/schema/*.schema.json not present.');
    process.exit(0);
  }

  const ajv = new Ajv({ allErrors: true, strict: false });
  const indexSchema = JSON.parse(fs.readFileSync(INDEX_SCHEMA_PATH, 'utf8'));
  const registrySchema = JSON.parse(fs.readFileSync(REGISTRY_SCHEMA_PATH, 'utf8'));
  const validateIndex = ajv.compile(indexSchema);
  const validateRegistry = ajv.compile(registrySchema);

  let failed = false;

  // 1. Validate spec index
  if (!fs.existsSync(INDEX_PATH)) {
    console.error('Spec Drift Issue: index file not found:', INDEX_PATH);
    process.exit(1);
  }
  const index = loadYaml(INDEX_PATH);
  if (!validateIndex(index)) {
    console.error('Schema validation FAILED: specs/specs.yml');
    console.error(validateIndex.errors);
    failed = true;
  }

  // 2. Validate each registry listed in specs[].file
  const specFiles = index?.specs || [];
  for (const entry of specFiles) {
    const file = entry.file;
    const regPath = path.join(SPECS_DIR, file);
    if (!fs.existsSync(regPath)) {
      console.error('Spec Drift Issue: registry file not found:', regPath);
      failed = true;
      continue;
    }
    const reg = loadYaml(regPath);
    const schemaId = reg?.schema?.id;
    if (schemaId === 'req-registry') {
      if (!validateRegistry(reg)) {
        console.error('Schema validation FAILED:', file);
        console.error(validateRegistry.errors);
        failed = true;
      }
    }
  }

  if (failed) {
    process.exit(1);
  }
  console.log('validate-specs: OK');
  process.exit(0);
}

main();
