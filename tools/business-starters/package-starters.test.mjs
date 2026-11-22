// Run: node --test tools/business-starters/
import assert from 'node:assert/strict';
import { cpSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { StarterError, checkPrefixes, packageStarters, readStarter } from './package-starters.mjs';

const HEAD = `manifestVersion: 3
id: inventory
name: Inventory
version: 1.0.0
definitionSchemaVersion: 1
keyPrefix: inv
`;
const CONTENTS = `contents:
  entities:
    - entities/e.yaml
  modules:
    - modules/m.yaml
  assets:
    - assets/modules/inv-stock/launcher.svg
`;
const MOUNT = `mounts:
  - moduleKey: inv-stock
    basePath: stock
    nav: { icon: inventory, labelKey: nav.title }
    launcher: { image: 'module:launcher.svg', titleKey: launcher.title }
`;

function starter(files) {
  const folder = mkdtempSync(join(tmpdir(), 'starter-'));
  const all = {
    'entities/e.yaml': 'entityDefinitions:\n  - code: inv-item\n',
    'modules/m.yaml': 'moduleDefinitions:\n  - key: inv-stock\n',
    'assets/modules/inv-stock/launcher.svg': '<svg/>',
    ...files,
  };
  for (const [path, content] of Object.entries(all)) {
    mkdirSync(dirname(join(folder, path)), { recursive: true });
    writeFileSync(join(folder, path), content);
  }
  return folder;
}

const refused = (folder, message) =>
  assert.throws(() => readStarter(folder), (error) => error instanceof StarterError && error.message.includes(message));

test('a domain starter with prefixed keys and mounts is accepted', () => {
  const { manifest } = readStarter(starter({ 'manifest.yaml': HEAD + CONTENTS + MOUNT }));
  assert.equal(manifest.keyPrefix, 'inv');
});

test('a key outside the prefix is refused', () => {
  refused(starter({ 'manifest.yaml': HEAD + CONTENTS, 'entities/e.yaml': 'entityDefinitions:\n  - code: item\n' }), "'item'");
});

test('a module scope outside the prefix is refused', () => {
  refused(
    starter({ 'manifest.yaml': HEAD + CONTENTS, 'modules/m.yaml': 'moduleDefinitions:\n  - key: inv-stock\n    translocoScope: stock\n' }),
    "transloco scope 'stock'",
  );
});

test('an application is refused, as are the files it owns', () => {
  refused(starter({ 'manifest.yaml': HEAD + CONTENTS + '  apps:\n    - apps/a.yaml\n', 'apps/a.yaml': 'appDefinitions: []\n' }), 'no application');
  refused(
    starter({
      'manifest.yaml': HEAD + CONTENTS + '  translations:\n    - translations/apps/app/en.json\n',
      'translations/apps/app/en.json': '{}',
    }),
    'belongs to an application',
  );
});

test('a bad prefix or a bad mount is refused', () => {
  refused(starter({ 'manifest.yaml': HEAD.replace('keyPrefix: inv', 'keyPrefix: in-v') + CONTENTS }), "'keyPrefix'");
  refused(starter({ 'manifest.yaml': HEAD + CONTENTS + MOUNT.replace('inv-stock', 'inv-other') }), 'does not define');
  refused(starter({ 'manifest.yaml': HEAD + CONTENTS + MOUNT.replace('basePath: stock', 'basePath: Stock') }), "'basePath'");
  refused(starter({ 'manifest.yaml': HEAD + CONTENTS + MOUNT.replace('module:launcher.svg', 'app:launcher.svg') }), 'asset of the module');
  refused(starter({ 'manifest.yaml': HEAD + CONTENTS + MOUNT.replace('module:launcher.svg', 'module:other.svg') }), 'listed under assets');
  refused(starter({ 'manifest.yaml': HEAD + CONTENTS + MOUNT.replace('labelKey', 'label') }), "unknown field 'label'");
});

test('a starter adds one sidenav entry and one launcher card, however many modules it mounts', () => {
  const second = { 'modules/m.yaml': 'moduleDefinitions:\n  - key: inv-stock\n  - key: inv-orders\n' };
  const routeOnly = '  - moduleKey: inv-orders\n    basePath: orders\n';
  const withNav = '  - moduleKey: inv-orders\n    basePath: orders\n    nav: { icon: list, labelKey: nav.title }\n';
  const withCard = '  - moduleKey: inv-orders\n    basePath: orders\n    launcher: { titleKey: launcher.title }\n';
  const manifest = (extra) => HEAD + CONTENTS + MOUNT + extra;

  readStarter(starter({ ...second, 'manifest.yaml': manifest(routeOnly) }));
  refused(starter({ ...second, 'manifest.yaml': manifest(withNav) }), "only one mount may carry a 'nav' or a 'launcher'");
  refused(starter({ ...second, 'manifest.yaml': manifest(withCard) }), 'inv-stock, inv-orders all do');
});

test('keyPrefix and mounts need manifestVersion 3', () => {
  refused(starter({ 'manifest.yaml': HEAD.replace('manifestVersion: 3', 'manifestVersion: 2') + CONTENTS }), "'keyPrefix' needs manifestVersion 3");
});

test('a prefix belongs to one starter, and to its common', () => {
  assert.throws(() => checkPrefixes({ starters: [{ id: 'other', keyPrefix: 'inv' }] }, [{ id: 'inventory', keyPrefix: 'inv' }]), StarterError);
  checkPrefixes({ starters: [{ id: 'inventory', keyPrefix: 'inv' }] }, [{ id: 'inventory-common', keyPrefix: 'inv' }]);
});

test('packaging refuses two starters with one prefix', () => {
  const source = mkdtempSync(join(tmpdir(), 'source-'));
  for (const id of ['inventory', 'stock']) {
    const folder = starter({ 'manifest.yaml': (HEAD + CONTENTS).replace('id: inventory', `id: ${id}`) });
    cpSync(folder, join(source, id), { recursive: true });
  }
  const out = mkdtempSync(join(tmpdir(), 'out-'));
  assert.throws(() => packageStarters({ source, out, publishedAt: '2026-11-19T00:00:00Z' }), /already/);
});
