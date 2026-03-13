/**
 * One-off: build specs/workflow-registry.yml from full core-registry (workflow domain only).
 * Reads specs/_core_full.yml, filters assets/requirements/features/dependencies by workflow domain, writes specs/workflow-registry.yml.
 */
const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

const ROOT = path.resolve(path.join(__dirname, '..'));
const CORE_PATH = path.join(ROOT, 'specs', '_core_full.yml');
const OUT_PATH = path.join(ROOT, 'specs', 'workflow-registry.yml');

const WORKFLOW_REQ_IDS = new Set(['REQ-WORKFLOW-001', 'REQ-LUNA-001']);
const WORKFLOW_ASSET_IDS = new Set([
  'ASSET-WORKFLOW', 'ASSET-WORKFLOW-RESULT', 'ASSET-STUB-WORKFLOW', 'ASSET-STUB-STATE',
  'ASSET-WORKFLOW-RUNNER', 'ASSET-WORKFLOW-RUN-RESULT', 'ASSET-STUB-WORKFLOW-RUNNER', 'ASSET-WORKFLOW-RUNNER-FACTORY',
  'ASSET-SESSION-KEY-STRATEGY', 'ASSET-SESSION-KEY-STRATEGIES', 'ASSET-CONFIGURABLE-WORKFLOW-STATE', 'ASSET-CONFIGURABLE-WORKFLOW-RUNNER',
  'ASSET-DYNAMIC-CHOICE-PROVIDER-REGISTRY', 'ASSET-STEP-RESULT', 'ASSET-STEP-OUTCOME', 'ASSET-WORKFLOW-STEP', 'ASSET-WORKFLOW-ACTION',
  'ASSET-WORKFLOW-ACTION-REGISTRY', 'ASSET-WORKFLOW-DEFINITION', 'ASSET-ASK-FOR-INPUT-STEP', 'ASSET-PROMPT-FOR-FIELD-STEP',
  'ASSET-CAPTURE-FIELD-STEP', 'ASSET-CALL-ACTION-STEP', 'ASSET-BRANCH-STEP', 'ASSET-DYNAMIC-CHOICE-PROVIDER', 'ASSET-DONE-STEP',
  'ASSET-LUNA-STATE', 'ASSET-LUNA-WORKFLOW', 'ASSET-CURSOR-ADAPTER', 'ASSET-CURSOR-ADAPTER-IMPL', 'ASSET-LUNA-RUN-STATE',
  'ASSET-CURSOR-RUN-MONITOR', 'ASSET-CURSOR-AGENT-LAUNCH-REQUEST', 'ASSET-CURSOR-AGENT-CONVERSATION', 'ASSET-CURSOR-AGENT-DETAILS',
  'ASSET-CURSOR-AGENT-LAUNCH-RESULT', 'ASSET-CURSOR-AGENT-MESSAGE', 'ASSET-CURSOR-CLOUD-TRANSPORT', 'ASSET-CURSOR-CLOUD-TRANSPORT-RESPONSE',
  'ASSET-CURSOR-CLOUD-EXCEPTION', 'ASSET-CURSOR-INSTRUCTION-COMPOSER', 'ASSET-CURSOR-FULL-RUN-TOOL', 'ASSET-GITHUB-REPOS-CHOICE-PROVIDER',
  'ASSET-BOTS-YAML', 'ASSET-BOOTSTRAP', 'ASSET-ENGINE', 'ASSET-LIFECYCLE-CONTEXT', 'ASSET-LIFECYCLE-CONTEXT-STORE',
  'ASSET-RUNTIME-BOT-INSTANCE', 'ASSET-CREATE-CHANNEL-ACTION', 'ASSET-POST-CHANNEL-MESSAGE-ACTION', 'ASSET-PROVISION-BOT-INSTANCE-ACTION',
  'ASSET-CREATE-LIFECYCLE-CONTEXT-ACTION', 'ASSET-LAUNCH-CURSOR-RUN-ACTION'
]);

function main() {
  const raw = fs.readFileSync(CORE_PATH, 'utf8');
  const doc = yaml.load(raw);
  if (!doc) throw new Error('Failed to parse _core_full.yml');

  const assets = (doc.assets || []).filter((a) => WORKFLOW_ASSET_IDS.has(a.id));
  const requirements = (doc.requirements || []).filter((r) => WORKFLOW_REQ_IDS.has(r.id));
  const features = (doc.features || []).filter((f) => f.domain_slug === 'workflow');
  const depItems = (doc.dependencies?.items || []).filter((d) =>
    (d.used_by_requirements || []).some((rid) => WORKFLOW_REQ_IDS.has(rid))
  );

  const out = {
    schema: { id: 'req-registry', version: '1.0.0', updated_utc: '2026-03-13T15:00:00Z' },
    project: doc.project,
    enums: doc.enums,
    dependencies: { items: depItems },
    assets,
    requirements,
    features
  };

  fs.writeFileSync(OUT_PATH, yaml.dump(out, { lineWidth: 120, noRefs: true }), 'utf8');
  console.log('Wrote', OUT_PATH);
}

main();
