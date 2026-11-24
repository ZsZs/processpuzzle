#!/usr/bin/env node
/**
 * Publishes the recorded tutorials: for every <tutorial>/<language> under tmp/out, uploads the MP4 to the object store
 * and creates or updates that language's row of the `tutorial` entity. The viewer in the designer reads those rows.
 *
 *   TUTORIAL_BASE_URL=http://localhost:9090 ENVIRONMENT=ci node apps/processpuzzle-testbed-tutorials/src/publish/publish.mjs [tutorial-name]
 *
 * Like the recorder, this talks to the backend as the testbed allows anonymous calls; it writes nothing else.
 */
import { readdir, readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(here, '..', '..', 'tmp', 'out');
const BASE = (process.env.TUTORIAL_BASE_URL ?? 'http://localhost:9090').replace(/\/?$/, '/');
const ENVIRONMENT = process.env.ENVIRONMENT ?? 'ci';
const ENTITY = 'tutorial';

async function runtimeConfig(file) {
  const response = await fetch(new URL(`run-time-conf/${file}`, BASE));
  return response.ok ? ((await response.json())?.BASE_CONFIGURATION ?? {}) : {};
}

/** The roots as the frontend reads them: config.common.json overlaid by config.<ENVIRONMENT>.json. */
async function roots() {
  const config = { ...(await runtimeConfig('config.common.json')), ...(await runtimeConfig(`config.${ENVIRONMENT}.json`)) };
  const absolute = (root) => new URL(root, BASE).toString().replace(/\/$/, '');
  return { backend: absolute(config.BACKEND_SERVICE_ROOT), objectStore: absolute(config.OBJECT_STORE_SERVICE_ROOT) };
}

async function check(response, what) {
  if (!response.ok) throw new Error(`${what} answered ${response.status}: ${await response.text()}`);
  return response;
}

async function upload(objectStore, file, name) {
  const form = new FormData();
  form.append('file', new Blob([await readFile(file)], { type: 'video/mp4' }), name);
  form.append('name', name);
  form.append('mimeType', 'video/mp4');
  const response = await check(await fetch(`${objectStore}/objects`, { method: 'POST', body: form }), `uploading ${name}`);
  const body = await response.json();
  return { bucket: response.headers.get('location') ?? body.bucketName ?? '', objectId: body.objectID, name, mimeType: 'video/mp4' };
}

async function findRow(backend, tutorialKey, language) {
  const rsql = encodeURIComponent(`tutorialKey==${tutorialKey};language==${language}`);
  const body = await (await check(await fetch(`${backend}/entities/${ENTITY}?size=5&rsql=${rsql}`), 'listing tutorials')).json();
  return body.content?.[0];
}

/** The transcript as the page shows it: the recording's own, without the heading it starts with. */
function transcriptOf(markdown) {
  return markdown.replace(/^#[^\n]*\n+/, '').trim();
}

async function publishLanguage(backend, objectStore, tutorialKey, language, directory) {
  const meta = JSON.parse(await readFile(path.join(directory, 'tutorial.json'), 'utf8'));
  const transcript = transcriptOf(await readFile(path.join(directory, `${tutorialKey}.md`), 'utf8'));
  const video = await upload(objectStore, path.join(directory, `${tutorialKey}.mp4`), `${tutorialKey}-${language}.mp4`);
  const payload = { tutorialKey, language, title: meta.title, transcript, video };

  const existing = await findRow(backend, tutorialKey, language);
  if (existing) {
    await check(
      await fetch(`${backend}/entities/${ENTITY}/${encodeURIComponent(existing.id)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ entityDefinitionCode: ENTITY, version: existing.version, payload }),
      }),
      `updating the ${language} tutorial`,
    );
    const previous = existing.payload?.video;
    if (previous?.objectId && previous.objectId !== video.objectId) {
      await fetch(`${objectStore}/objects/${encodeURIComponent(previous.bucket)}/${encodeURIComponent(previous.objectId)}`, { method: 'DELETE' });
    }
    return 'updated';
  }
  await check(
    await fetch(`${backend}/entities/${ENTITY}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ entityDefinitionCode: ENTITY, payload }),
    }),
    `creating the ${language} tutorial`,
  );
  return 'created';
}

async function main() {
  const only = process.argv[2];
  const { backend, objectStore } = await roots();
  const tutorials = (await readdir(OUT)).filter((name) => !only || name === only);
  const scope = only ? ` for ${only}` : '';
  if (tutorials.length === 0) throw new Error(`nothing recorded${scope} in ${OUT}`);
  for (const tutorialKey of tutorials) {
    for (const language of await readdir(path.join(OUT, tutorialKey))) {
      const directory = path.join(OUT, tutorialKey, language);
      if (!(await stat(path.join(directory, `${tutorialKey}.mp4`)).catch(() => null))) {
        console.warn(`[publish] ${tutorialKey}/${language}: no video, skipped`);
        continue;
      }
      const outcome = await publishLanguage(backend, objectStore, tutorialKey, language, directory);
      console.log(`[publish] ${tutorialKey}/${language}: ${outcome}`);
    }
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
