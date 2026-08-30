const fs = require('fs');
const path = require('path');
const yaml = require('js-yaml');

// Metadata is scoped per organization: every source file is named `<orgKey>-<kind>.yaml`, exactly as
// SampleRuleLoader and DefaultAppLoader expect it on the backend, so seeding another tenant is adding
// another file rather than editing this script.
const METADATA_SOURCES = [
  {
    directory: '../../libs/java-shared/base-rule-backend/src/main/resources/sample-rules',
    suffix: '-rules.yaml',
    documentKey: 'rules',
    collection: 'rules',
  },
  {
    directory: '../../libs/java-shared/base-app-backend/src/main/resources/default-apps',
    suffix: '-apps.yaml',
    documentKey: 'appDefinitions',
    collection: 'app-definitions',
  },
  // Modules live in the same file as the apps that mount them, under a key of their own — one source
  // per collection, so the apps file simply appears twice here. A module is identified by `key` in the
  // contract, and json-server addresses records by `id`, so `identifier` names the field to mirror.
  {
    directory: '../../libs/java-shared/base-app-backend/src/main/resources/default-apps',
    suffix: '-apps.yaml',
    documentKey: 'moduleDefinitions',
    collection: 'modules',
    identifier: 'key',
  },
  // A state machine is identified by the entity type it governs — `/state-machines/{entityName}`, see
  // base-state-api.yaml — so that is the field mirrored onto json-server's `id`. The states in this file
  // spell their flags `isFinal` / `isLocked`, because DefaultStateImporter binds them to base-state's own
  // `State` record; `StateMachineDefinitionMapper` reads either spelling, so the frontend sees the same
  // machine whether it is served from here or from the Spring backend.
  {
    directory: '../../libs/java-shared/base-state-backend/src/main/resources/default-state-machines',
    suffix: '-state-machines.yaml',
    documentKey: 'stateMachines',
    collection: 'state-machines',
    identifier: 'entityName',
  },
  // A process definition is identified by the author-chosen id the YAML already carries —
  // `/processes/{processId}`, see base-workflow-api.yaml — which is also what json-server keys a record
  // by, so no `identifier` rename is needed here. The file has no `tools:` or instance sections: a
  // ToolDefinition is a resource of its own with no YAML loader on the backend, and an instance only
  // exists once a process has been started, so both are hand-written fixtures in db.json.
  {
    directory: '../../libs/java-shared/base-workflow-backend/src/main/resources/default-workflows',
    suffix: '-workflows.yaml',
    documentKey: 'processes',
    collection: 'processes',
  },
];

// json-server rejects '/' in collection names, so an org-scoped collection is flattened to
// `<orgKey>-<collection>`; org-scope.js rewrites `/organizations/<orgKey>/<collection>` onto it.
const GENERATED_COLLECTION = /(^organizations$)|(-rules$)|(-app-definitions$)|(-modules$)|(-state-machines$)|(-processes$)/;

function readYamlDocuments({ directory, suffix, documentKey, collection, identifier }) {
  const absoluteDirectory = path.resolve(__dirname, directory);
  if (!fs.existsSync(absoluteDirectory)) return [];
  return fs
    .readdirSync(absoluteDirectory)
    .filter((fileName) => fileName.endsWith(suffix))
    .map((fileName) => {
      const document = yaml.load(fs.readFileSync(path.join(absoluteDirectory, fileName), 'utf8')) ?? {};
      return {
        orgKey: fileName.slice(0, -suffix.length),
        organization: document.organization,
        collection,
        records: (document[documentKey] ?? []).map((record) => withIdentifier(record, identifier)),
      };
    });
}

// json-server addresses a record by its `id`, so a collection whose contract identifier is named
// something else gets that field mirrored onto `id` — the same rename the frontend mapper performs.
function withIdentifier(record, identifier) {
  if (!identifier || record?.id !== undefined) return record;
  return { id: record?.[identifier], ...record };
}

// The `organization` block of an apps file provisions the tenant; a file that carries none still
// implies its organization exists, so the file name alone is enough to register it.
function toOrganization(orgKey, organization) {
  return { ...organization, id: orgKey, key: orgKey, name: organization?.name ?? orgKey };
}

module.exports = () => {
  const db = JSON.parse(fs.readFileSync(path.join(__dirname, 'db.json'), 'utf8'));
  const documents = METADATA_SOURCES.flatMap(readYamlDocuments);

  const organizations = new Map();
  const collections = {};
  for (const { orgKey, organization, collection, records } of documents) {
    if (organization || !organizations.has(orgKey)) organizations.set(orgKey, toOrganization(orgKey, organization));
    collections[`${orgKey}-${collection}`] = records;
  }

  // db.json is both the fixture read here and the merge target written by CI, so drop what a previous
  // merge generated — otherwise a renamed or deleted YAML leaves its collection behind forever.
  const fixtures = Object.fromEntries(Object.entries(db).filter(([key]) => !GENERATED_COLLECTION.test(key)));
  return { ...fixtures, organizations: [...organizations.values()], ...collections };
};
