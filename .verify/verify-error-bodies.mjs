/**
 * Live check of plan items 4 and 5 against the running emulator: every refusal must answer a body that
 * is exactly {errorId, errorText} — no third key — and the referring block ids must survive inside
 * errorText now that `referencingBlockIds` is gone.
 */
const FN = 'http://127.0.0.1:5001/demo-processpuzzle-testbed/europe-central2/baseDocument';
const HOSTING = 'http://127.0.0.1:6002/api';
const ORG = 'error-shape-check';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

let failures = 0;

function check(label, condition, detail) {
  console.log(`${condition ? 'PASS' : 'FAIL'}  ${label}${condition ? '' : ` -> ${detail}`}`);
  if (!condition) failures += 1;
}

/** The invariant under test, applied to every refusal rather than to a chosen one. */
async function expectErrorBody(label, response, status, errorId, textIncludes) {
  const raw = await response.text();
  let body;
  try {
    body = JSON.parse(raw);
  } catch {
    check(`${label}: answers JSON at all`, false, `${response.status} ${response.headers.get('content-type')} :: ${raw.slice(0, 120).replace(/\n/g, ' ')}`);
    return undefined;
  }
  const keys = Object.keys(body).sort();
  check(`${label}: status ${status}`, response.status === status, `got ${response.status}`);
  check(`${label}: body is exactly {errorId, errorText}`, JSON.stringify(keys) === '["errorId","errorText"]', keys.join(','));
  check(`${label}: errorId ${errorId}`, body.errorId === errorId, body.errorId);
  if (textIncludes) check(`${label}: errorText names ${textIncludes}`, String(body.errorText).includes(textIncludes), body.errorText);
  console.log(`      ${JSON.stringify(body)}`);
  return body;
}

const post = (url, body) => fetch(url, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) });

const documents = (base = FN) => `${base}/organizations/${ORG}/documents`;
const properties = { slug: `err-${Date.now()}`, title: 'Error shape check', sourceLocale: 'en' };

// 1. a document to work against
const created = await post(documents(), properties);
check('create document: 201', created.status === 201, `${created.status} ${await created.clone().text()}`);
const document = await created.json();
console.log(`      created id=${document.id} slug=${document.slug}`);

// 2. duplicate slug — the cross-backend case named in the plan
await expectErrorBody('duplicate slug', await post(documents(), properties), 409, 'document.slug.already-exists', properties.slug);

// 3. same refusal through the Hosting rewrite, to prove the gateway path does not reshape the body
await expectErrorBody('duplicate slug via Hosting rewrite', await post(documents(HOSTING), properties), 409, 'document.slug.already-exists', properties.slug);

// 4. a widget, then a paragraph embedding it, then delete the widget -> referenced
const blocks = `${documents()}/${document.id}/translations/en/blocks`;
const widget = await (await post(blocks, { kind: 'WIDGET', placement: 'STANDALONE', type: 'entity-table', props: { entity: 'order' } })).json();
const embedding = { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'widgetEmbed', attrs: { blockId: widget.id } }] }] };
const text = await (await post(blocks, { kind: 'TEXT', editable: true, content: embedding })).json();
console.log(`      widget=${widget.id} embedded by text=${text.id}`);

await expectErrorBody('delete a referenced block', await fetch(`${blocks}/${widget.id}`, { method: 'DELETE' }), 409, 'document.block.referenced', text.id);

// 5. the other statuses, so the shape is checked on 404/400 too
await expectErrorBody('unknown document', await fetch(`${documents()}/no-such-doc`), 404, 'document.not-found');
await expectErrorBody('unknown block', await fetch(`${blocks}/no-such-block`, { method: 'DELETE' }), 404, 'document.block.not-found');
await expectErrorBody('malformed JSON body', await fetch(documents(), { method: 'POST', headers: JSON_HEADERS, body: '{ "slug": ' }), 400, 'request.malformed-payload');
await expectErrorBody('invalid input', await post(documents(), { slug: 'Not A Slug', title: 'x', sourceLocale: 'en' }), 400, 'document.input.invalid');

// housekeeping: leave the emulator as clean as a fixture-less check can
await fetch(`${documents()}/${document.id}`, { method: 'DELETE' });

console.log(failures === 0 ? '\nAll live error bodies conform.' : `\n${failures} check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
