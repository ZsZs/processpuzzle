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
];

// json-server rejects '/' in collection names, so an org-scoped collection is flattened to
// `<orgKey>-<collection>`; org-scope.js rewrites `/organizations/<orgKey>/<collection>` onto it.
const GENERATED_COLLECTION = /(^organizations$)|(-rules$)|(-app-definitions$)/;

function readYamlDocuments({ directory, suffix, documentKey, collection }) {
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
        records: document[documentKey] ?? [],
      };
    });
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
