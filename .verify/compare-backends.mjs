/**
 * Plan item 5, live: the same error, asked of both backends, must come back as the same body — that is the
 * whole point of the change. Java runs from the current source on 8090 (the 8080 instance is an older jar);
 * the Cloud Function runs in the emulator.
 */
const JAVA = 'http://localhost:8090';
const FN = 'http://127.0.0.1:5001/demo-processpuzzle-testbed/europe-central2/baseDocument';
const ORG = 'compare-check';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

let mismatches = 0;

const documents = (base) => `${base}/organizations/${ORG}/documents`;
const post = (url, body) => fetch(url, { method: 'POST', headers: JSON_HEADERS, body: typeof body === 'string' ? body : JSON.stringify(body) });

async function read(response) {
  const raw = await response.text();
  try {
    return { status: response.status, body: JSON.parse(raw) };
  } catch {
    return { status: response.status, body: `<non-JSON ${response.headers.get('content-type')}> ${raw.slice(0, 60).replace(/\s+/g, ' ')}` };
  }
}

/** Compares status and errorId; errorText wording is allowed to differ — it is only the human fallback. */
async function compare(label, javaResponse, functionResponse) {
  const java = await read(javaResponse);
  const fn = await read(functionResponse);
  const same = java.status === fn.status && java.body?.errorId === fn.body?.errorId && java.body?.errorId !== undefined;
  if (!same) mismatches += 1;
  console.log(`${same ? 'MATCH   ' : 'DIVERGE '} ${label}`);
  console.log(`         java ${java.status} ${JSON.stringify(java.body)}`);
  console.log(`         func ${fn.status} ${JSON.stringify(fn.body)}`);
}

const properties = { slug: `cmp-${Date.now()}`, title: 'Cross-backend comparison', sourceLocale: 'en' };

// Seed the same document in both, so the duplicate-slug refusal is reachable on each side.
for (const base of [JAVA, FN]) {
  const created = await post(documents(base), properties);
  if (created.status !== 201) console.log(`SETUP    create on ${base} -> ${created.status} ${(await created.text()).slice(0, 200)}`);
}

await compare('duplicate slug', await post(documents(JAVA), properties), await post(documents(FN), properties));

const absent = '11111111-1111-1111-1111-111111111111'; // well-formed uuid so Java gets past parsing
await compare('unknown document', await fetch(`${documents(JAVA)}/${absent}`), await fetch(`${documents(FN)}/${absent}`));

await compare('invalid input (slug not a slug)', await post(documents(JAVA), { ...properties, slug: 'Not A Slug' }), await post(documents(FN), { ...properties, slug: 'Not A Slug' }));

await compare('malformed JSON body', await post(documents(JAVA), '{ "slug": '), await post(documents(FN), '{ "slug": '));

// Not a uuid at all: Java parses document ids as UUIDs, Firestore ids are opaque strings.
await compare('non-uuid document id', await fetch(`${documents(JAVA)}/no-such-doc`), await fetch(`${documents(FN)}/no-such-doc`));

console.log(mismatches === 0 ? '\nBoth backends answer identically.' : `\n${mismatches} divergence(s).`);
