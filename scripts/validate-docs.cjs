/**
 * validate-docs.cjs — Docs Drift Gate
 * Verifies mkdoc feature/domain artifacts and mkdocs nav are synchronized with current specs.
 */

const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const ROOT = path.resolve(process.env.VINEKEEPERS_ROOT || path.join(__dirname, '..'));
const PROJECT_CONFIG_PATH = path.join(ROOT, '.cursor', 'project.yml');
const REQUIRED_SUBPAGES = [
  'how-it-works.md',
  'change-log.md',
  'known-issues.md',
  'decisions.md',
  'contracts.md',
  'tests.md',
  'diagrams.md',
];

function loadYaml(filePath) {
  return yaml.load(fs.readFileSync(filePath, 'utf8'));
}

function loadYamlRelaxed(filePath) {
  const raw = fs
    .readFileSync(filePath, 'utf8')
    .replace(/!!python\/name:([^\r\n]+)/g, '"$1"');
  return yaml.load(raw);
}

function toPosix(relPath) {
  return relPath.split(path.sep).join('/');
}

function existsRelative(relPath) {
  return fs.existsSync(path.join(ROOT, relPath));
}

function collectMarkdownFiles(dirPath) {
  const results = [];
  if (!fs.existsSync(dirPath)) {
    return results;
  }

  for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
    const fullPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      results.push(...collectMarkdownFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      results.push(fullPath);
    }
  }

  return results;
}

function flattenNavLeaves(value, output) {
  if (typeof value === 'string') {
    output.push(value);
    return;
  }

  if (Array.isArray(value)) {
    for (const item of value) {
      flattenNavLeaves(item, output);
    }
    return;
  }

  if (value && typeof value === 'object') {
    for (const nested of Object.values(value)) {
      flattenNavLeaves(nested, output);
    }
  }
}

function hasHeading(relPath) {
  const fullPath = path.join(ROOT, relPath);
  if (!fs.existsSync(fullPath)) {
    return false;
  }

  const content = fs.readFileSync(fullPath, 'utf8');
  return /^\s*#\s+/m.test(content);
}

function isSafeRelativeDocPath(relPath) {
  if (typeof relPath !== 'string' || relPath.length === 0) {
    return false;
  }

  if (path.isAbsolute(relPath)) {
    return false;
  }

  const normalized = path.posix.normalize(relPath.replace(/\\/g, '/'));
  return !normalized.startsWith('../') && normalized !== '..';
}

function main() {
  const projectConfig = fs.existsSync(PROJECT_CONFIG_PATH) ? loadYaml(PROJECT_CONFIG_PATH) : {};
  const specsIndexRelPath = projectConfig.paths?.specs_index || 'specs/specs.yml';
  const docsDirRelPath = projectConfig.paths?.docs_dir || 'mkdoc';
  const mkdocsRelPath = projectConfig.paths?.mkdocs_file || 'mkdocs.yml';
  const specsIndexPath = path.join(ROOT, specsIndexRelPath);
  const docsDirPath = path.join(ROOT, docsDirRelPath);
  const featureDomainDirPath = path.join(docsDirPath, 'features', 'domain');

  let failed = false;

  if (!fs.existsSync(specsIndexPath)) {
    console.error('Docs drift: index not found:', specsIndexRelPath);
    process.exit(1);
  }

  const index = loadYaml(specsIndexPath);
  const declaredDomains = new Set((index.domains || []).map((domain) => domain.slug));
  const expectedDomains = new Set();
  const expectedFeatureDocs = [];
  const expectedPaths = new Set([
    'features/index.md',
    'features/domain.md',
  ]);

  for (const entry of index.specs || []) {
    const registryPath = path.join(path.dirname(specsIndexPath), entry.file);
    if (!fs.existsSync(registryPath)) {
      continue;
    }

    const registry = loadYaml(registryPath);
    const registryStem = entry.file.replace(/-registry\.yml$/, '');
    const features = registry.features || [];

    if (features.length === 0) {
      const domainSlug = registryStem;
      expectedDomains.add(domainSlug);
      const summaryPath = `features/domain/${domainSlug}/${domainSlug}.md`;
      expectedFeatureDocs.push({ slug: domainSlug, domainSlug, summaryPath });
      expectedPaths.add(summaryPath);
      continue;
    }

    for (const feature of features) {
      const domainSlug = feature.domain_slug || registryStem;
      const summaryPath = feature.doc_path || `features/domain/${domainSlug}/${feature.slug}.md`;
      if (declaredDomains.size > 0 && !declaredDomains.has(domainSlug)) {
        console.error('Docs drift: feature domain_slug not declared in specs.yml domains[]:', feature.id, '->', domainSlug);
        failed = true;
      }
      if (!isSafeRelativeDocPath(summaryPath)) {
        console.error('Docs drift: invalid feature doc_path:', feature.id, '->', summaryPath);
        failed = true;
        continue;
      }
      expectedDomains.add(domainSlug);
      expectedFeatureDocs.push({ slug: feature.slug, domainSlug, summaryPath });
      expectedPaths.add(summaryPath);
    }
  }

  for (const domainSlug of expectedDomains) {
    expectedPaths.add(`features/domain/${domainSlug}.md`);
  }

  for (const feature of expectedFeatureDocs) {
    const summaryDir = path.posix.dirname(feature.summaryPath);
    for (const subpage of REQUIRED_SUBPAGES) {
      expectedPaths.add(`${summaryDir}/${feature.slug}/${subpage}`);
    }
  }

  for (const relPath of expectedPaths) {
    if (!existsRelative(path.posix.join(docsDirRelPath, relPath).replace(/\\/g, '/'))) {
      console.error('Docs drift: missing docs file:', path.posix.join(docsDirRelPath, relPath));
      failed = true;
      continue;
    }

    if (relPath.endsWith('.md') && !hasHeading(path.posix.join(docsDirRelPath, relPath))) {
      console.error('Docs drift: markdown file missing heading:', path.posix.join(docsDirRelPath, relPath));
      failed = true;
    }
  }

  const actualFeatureDomainFiles = collectMarkdownFiles(featureDomainDirPath).map((fullPath) =>
    toPosix(path.relative(docsDirPath, fullPath))
  );

  for (const relPath of actualFeatureDomainFiles) {
    if (!expectedPaths.has(relPath)) {
      console.error('Docs drift: stale feature/domain doc:', path.posix.join(docsDirRelPath, relPath));
      failed = true;
    }
  }

  const mkdocsPath = path.join(ROOT, mkdocsRelPath);
  if (!fs.existsSync(mkdocsPath)) {
    console.error('Docs drift: mkdocs config missing:', mkdocsRelPath);
    failed = true;
  } else {
    const mkdocsConfig = loadYamlRelaxed(mkdocsPath);
    const navLeaves = [];
    flattenNavLeaves(mkdocsConfig.nav || [], navLeaves);
    const navSet = new Set(navLeaves);

    const requiredNavPaths = new Set([
      'features/index.md',
      'features/domain.md',
      ...Array.from(expectedDomains).map((domainSlug) => `features/domain/${domainSlug}.md`),
      ...expectedFeatureDocs.map((feature) => feature.summaryPath),
    ]);

    for (const relPath of requiredNavPaths) {
      if (!navSet.has(relPath)) {
        console.error('Docs drift: mkdocs nav missing path:', relPath);
        failed = true;
      }
    }

    for (const relPath of navSet) {
      if (typeof relPath !== 'string') {
        continue;
      }

      if (!relPath.startsWith('features/domain')) {
        continue;
      }

      if (
        relPath !== 'features/domain.md' &&
        !requiredNavPaths.has(relPath)
      ) {
        console.error('Docs drift: stale mkdocs nav entry:', relPath);
        failed = true;
      }
    }
  }

  if (failed) {
    process.exit(1);
  }

  console.log('validate-docs: OK');
}

main();
