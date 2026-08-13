import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DocumentAccessPolicy } from './document-access-policy.js';
import type { DocumentStoreStub, RunningApp } from './test-support.js';

vi.mock('firebase-functions', () => ({ logger: { info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const { logger } = await import('firebase-functions');
const { BaseDocumentHandlers } = await import('./base-document.handlers.js');
const { createBaseDocumentApp } = await import('./base-document.function.js');
const { anEmbeddingBlock, aStoredDocument, aStoredDraft, aTextBlock, aWidgetBlock, createDocumentStoreStub, ORG_KEY, seedDocument, serve } = await import('./test-support.js');

const DOCUMENT_ID = '11111111-1111-1111-1111-111111111111';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

let stub: DocumentStoreStub;
let app: RunningApp;

async function start(policy?: DocumentAccessPolicy): Promise<void> {
  app = await serve(createBaseDocumentApp(new BaseDocumentHandlers(stub.store, policy)));
}

const documentsUrl = () => `${app.base}/organizations/${ORG_KEY}/documents`;
const documentUrl = (documentId = DOCUMENT_ID) => `${documentsUrl()}/${documentId}`;
const translationsUrl = (documentId = DOCUMENT_ID) => `${documentUrl(documentId)}/translations`;
const blocksUrl = (locale = 'en', documentId = DOCUMENT_ID) => `${translationsUrl(documentId)}/${locale}/blocks`;

const post = (url: string, body: unknown) => fetch(url, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) });
const put = (url: string, body: unknown) => fetch(url, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify(body) });

const validProperties = { slug: 'q3-plan', title: 'Q3 plan', sourceLocale: 'en' };
const aTextBlockInput = { kind: 'TEXT', editable: true, content: { type: 'doc', content: [{ type: 'paragraph' }] } };

beforeEach(() => {
  vi.clearAllMocks();
  stub = createDocumentStoreStub();
});

afterEach(async () => {
  await app?.close();
});

describe('listDocuments', () => {
  it('answers the contract page shape', async () => {
    seedDocument(stub);
    await start();

    const response = await fetch(documentsUrl());
    expect(response.status).toBe(200);

    const page = await response.json();
    expect(Object.keys(page).sort()).toEqual(['content', 'number', 'size', 'totalElements', 'totalPages']);
    expect(page.totalElements).toBe(1);
    expect(page.content[0].id).toBe(DOCUMENT_ID);
  });

  it('reports an exact total rather than the size of the page it returned', async () => {
    for (let index = 0; index < 3; index += 1) seedDocument(stub, aStoredDocument({ id: `doc-${index}`, slug: `slug-${index}` }));
    await start();

    const page = await (await fetch(`${documentsUrl()}?page=0&size=2`)).json();
    expect(page.content).toHaveLength(2);
    expect(page.totalElements).toBe(3);
    expect(page.totalPages).toBe(2);
  });

  it('summarises each document without its content', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    const page = await (await fetch(documentsUrl())).json();
    expect(page.content[0]).not.toHaveProperty('translation');
    expect(page.content[0].translations).toEqual([expect.objectContaining({ locale: 'en', blockCount: 1, status: 'DRAFT' })]);
  });

  it('never leaks another organization documents', async () => {
    seedDocument(stub, aStoredDocument({ orgKey: 'other-org', id: 'other-doc' }));
    await start();

    expect((await (await fetch(documentsUrl())).json()).totalElements).toBe(0);
  });

  it('applies an RSQL filter', async () => {
    seedDocument(stub, aStoredDocument({ id: 'public-doc', slug: 'public', isPublic: true }));
    seedDocument(stub, aStoredDocument({ id: 'private-doc', slug: 'private' }));
    await start();

    const page = await (await fetch(`${documentsUrl()}?where=isPublic==true`)).json();
    expect(page.content.map((document: { id: string }) => document.id)).toEqual(['public-doc']);
  });

  it('rejects a filter it cannot evaluate instead of returning everything', async () => {
    await start();

    const response = await fetch(`${documentsUrl()}?where=title=like=plan`);
    expect(response.status).toBe(400);
    expect((await response.json()).errorId).toBe('document.query.unsupported-filter');
  });

  it('warns when the in-memory scan starts truncating', async () => {
    // MAX_LIST_SCAN documents read means there may be more the filter never saw.
    vi.resetModules();
    vi.doMock('./base-document.config.js', async (importOriginal) => ({ ...(await importOriginal<object>()), MAX_LIST_SCAN: 1 }));
    const { BaseDocumentHandlers: Handlers } = await import('./base-document.handlers.js');
    const { createBaseDocumentApp: createApp } = await import('./base-document.function.js');

    seedDocument(stub, aStoredDocument({ id: 'doc-1', slug: 'one' }));
    seedDocument(stub, aStoredDocument({ id: 'doc-2', slug: 'two' }));
    app = await serve(createApp(new Handlers(stub.store)));

    await fetch(documentsUrl());
    expect(logger.warn).toHaveBeenCalledWith(expect.stringContaining('truncating'));
    vi.doUnmock('./base-document.config.js');
  });
});

describe('createDocument', () => {
  it('creates the document and answers 201 with the stored representation', async () => {
    await start();

    const response = await post(documentsUrl(), validProperties);
    expect(response.status).toBe(201);

    const document = await response.json();
    expect(document).toMatchObject({ orgKey: ORG_KEY, slug: 'q3-plan', title: 'Q3 plan', lockVersion: 0, publishedAt: null });
    expect(stub.documents.get(`${ORG_KEY}/${document.id}`)).toBeDefined();
  });

  it('mints the id and ignores one supplied by the client', async () => {
    await start();

    const document = await (await post(documentsUrl(), { ...validProperties, id: 'client-chosen' })).json();
    expect(document.id).not.toBe('client-chosen');
  });

  it('creates the source-locale draft, without which the first block append would 404', async () => {
    await start();

    const document = await (await post(documentsUrl(), validProperties)).json();
    expect(stub.drafts.get(`${ORG_KEY}/${document.id}/en`)).toMatchObject({ locale: 'en', revision: 1, basedOnRevision: null, blocks: [] });
    expect(document.translation).toMatchObject({ locale: 'en', status: 'DRAFT', blocks: [] });
  });

  it('persists none of the extra keys the frontend spreads into the body', async () => {
    await start();

    const document = await (await post(documentsUrl(), { ...validProperties, translations: [], translation: null, version: 5 })).json();
    const stored = stub.documents.get(`${ORG_KEY}/${document.id}`) as unknown as Record<string, unknown>;
    expect(stored).not.toHaveProperty('translations');
    expect(stored).not.toHaveProperty('translation');
    expect(stored).not.toHaveProperty('version');
  });

  it('takes the source locale blocks from the create payload when it carries them', async () => {
    await start();

    const document = await (await post(documentsUrl(), { ...validProperties, translations: [{ locale: 'en', blocks: [aTextBlockInput] }] })).json();
    expect(stub.drafts.get(`${ORG_KEY}/${document.id}/en`)?.blocks).toHaveLength(1);
  });

  it('rejects a slug already taken in the organization', async () => {
    seedDocument(stub);
    await start();

    const response = await post(documentsUrl(), validProperties);
    expect(response.status).toBe(409);
    expect((await response.json()).errorId).toBe('document.slug.already-exists');
  });

  it('allows the same slug in a different organization', async () => {
    seedDocument(stub, aStoredDocument({ orgKey: 'other-org', id: 'other-doc' }));
    await start();

    expect((await post(documentsUrl(), validProperties)).status).toBe(201);
  });

  it.each([
    ['a missing slug', { title: 'Q3 plan', sourceLocale: 'en' }],
    ['a slug that breaks the pattern', { ...validProperties, slug: 'Not A Slug' }],
    ['a missing title', { slug: 'q3-plan', sourceLocale: 'en' }],
    ['a blank title', { ...validProperties, title: '   ' }],
    ['a missing source locale', { slug: 'q3-plan', title: 'Q3 plan' }],
    ['a source locale that breaks the pattern', { ...validProperties, sourceLocale: 'english' }],
  ])('rejects %s', async (_description, body) => {
    await start();

    const response = await post(documentsUrl(), body);
    expect(response.status).toBe(400);
    expect((await response.json()).errorId).toBe('document.input.invalid');
  });
});

describe('getDocument', () => {
  it('returns the source locale translation by default', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    const document = await (await fetch(`${documentUrl()}?draft=true`)).json();
    expect(document.translation).toMatchObject({ locale: 'en', blocks: [aTextBlock('b1')] });
  });

  it('returns the requested locale', async () => {
    seedDocument(stub);
    stub.drafts.set(`${ORG_KEY}/${DOCUMENT_ID}/hu`, aStoredDraft({ locale: 'hu', blocks: [aTextBlock('hu-1')] }));
    await start();

    const document = await (await fetch(`${documentUrl()}?locale=hu&draft=true`)).json();
    expect(document.translation.locale).toBe('hu');
  });

  it('carries a null translation for a locale that has none', async () => {
    seedDocument(stub);
    await start();

    const document = await (await fetch(`${documentUrl()}?locale=de&draft=true`)).json();
    expect(document.translation).toBeNull();
  });

  it('carries a null translation when the draft is not requested and nothing is published', async () => {
    seedDocument(stub);
    await start();

    expect((await (await fetch(documentUrl())).json()).translation).toBeNull();
  });

  it('answers 404 for an unknown document', async () => {
    await start();

    const response = await fetch(documentUrl('22222222-2222-2222-2222-222222222222'));
    expect(response.status).toBe(404);
    expect((await response.json()).errorId).toBe('document.not-found');
  });

  it('answers 404 rather than reaching the driver for an id Firestore would reject', async () => {
    await start();

    expect((await fetch(documentUrl('__proto__'))).status).toBe(404);
  });
});

describe('updateDocument', () => {
  it('replaces properties and the named locale content', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    const response = await put(documentUrl(), { ...validProperties, title: 'Renamed', translations: [{ locale: 'en', blocks: [aTextBlockInput] }] });
    expect(response.status).toBe(200);
    expect((await response.json()).title).toBe('Renamed');
    expect(stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`)?.revision).toBe(2);
  });

  it('leaves content alone when no locale names blocks', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    await put(documentUrl(), { ...validProperties, title: 'Renamed' });
    expect(stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`)).toMatchObject({ revision: 1, blocks: [aTextBlock('b1')] });
  });

  it('never implicitly creates a translation, and writes nothing when one is unknown', async () => {
    seedDocument(stub);
    await start();

    const response = await put(documentUrl(), { ...validProperties, title: 'Renamed', translations: [{ locale: 'de', blocks: [] }] });
    expect(response.status).toBe(404);
    expect((await response.json()).errorId).toBe('document.translation.not-found');
    expect(stub.documents.get(`${ORG_KEY}/${DOCUMENT_ID}`)?.title).toBe('Q3 plan');
  });

  it('rejects a slug owned by another document', async () => {
    seedDocument(stub);
    seedDocument(stub, aStoredDocument({ id: 'other-doc', slug: 'taken' }));
    await start();

    const response = await put(documentUrl(), { ...validProperties, slug: 'taken' });
    expect(response.status).toBe(409);
  });

  it('accepts the document keeping its own slug', async () => {
    seedDocument(stub);
    await start();

    expect((await put(documentUrl(), { ...validProperties, title: 'Renamed' })).status).toBe(200);
  });

  it('rejects an invalid body', async () => {
    seedDocument(stub);
    await start();

    expect((await put(documentUrl(), { title: 'No slug' })).status).toBe(400);
  });
});

describe('updateDocumentProperties', () => {
  it('saves the properties and bumps lockVersion', async () => {
    seedDocument(stub, aStoredDocument({ lockVersion: 2 }));
    await start();

    const response = await put(`${documentUrl()}/properties`, { ...validProperties, title: 'Renamed', subject: 'Planning' });
    expect(response.status).toBe(200);

    const document = await response.json();
    expect(document).toMatchObject({ title: 'Renamed', subject: 'Planning', lockVersion: 3 });
  });

  it('keeps the content, which is the regression this endpoint exists to avoid', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    await put(`${documentUrl()}/properties`, { ...validProperties, title: 'Renamed' });
    expect(stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`)).toMatchObject({ revision: 1, blocks: [aTextBlock('b1')] });
  });

  it('rejects a slug owned by another document', async () => {
    seedDocument(stub);
    seedDocument(stub, aStoredDocument({ id: 'other-doc', slug: 'taken' }));
    await start();

    expect((await put(`${documentUrl()}/properties`, { ...validProperties, slug: 'taken' })).status).toBe(409);
  });

  it('answers 404 for an unknown document', async () => {
    await start();

    expect((await put(`${documentUrl()}/properties`, validProperties)).status).toBe(404);
  });

  it('rejects an invalid body', async () => {
    seedDocument(stub);
    await start();

    expect((await put(`${documentUrl()}/properties`, { slug: 'q3-plan' })).status).toBe(400);
  });
});

describe('deleteDocument', () => {
  it('removes the document and every translation beneath it', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    stub.drafts.set(`${ORG_KEY}/${DOCUMENT_ID}/hu`, aStoredDraft({ locale: 'hu' }));
    await start();

    expect((await fetch(documentUrl(), { method: 'DELETE' })).status).toBe(204);
    expect(stub.documents.size).toBe(0);
    expect(stub.drafts.size).toBe(0);
  });

  it('answers 404 for an unknown document', async () => {
    await start();

    expect((await fetch(documentUrl(), { method: 'DELETE' })).status).toBe(404);
  });
});

describe('listDocumentTranslations', () => {
  it('returns a bare array of summaries, source locale first', async () => {
    seedDocument(stub);
    stub.drafts.set(`${ORG_KEY}/${DOCUMENT_ID}/hu`, aStoredDraft({ locale: 'hu' }));
    await start();

    const summaries = await (await fetch(translationsUrl())).json();
    expect(Array.isArray(summaries)).toBe(true);
    expect(summaries.map((summary: { locale: string }) => summary.locale)).toEqual(['en', 'hu']);
  });

  it('answers 404 for an unknown document', async () => {
    await start();

    expect((await fetch(translationsUrl())).status).toBe(404);
  });
});

describe('addDocumentTranslation', () => {
  it('copies the source locale content when blocks are absent', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    const response = await post(translationsUrl(), { locale: 'hu' });
    expect(response.status).toBe(201);
    expect((await response.json()).blocks).toEqual([aTextBlock('b1')]);
  });

  it('starts a blank page for an explicit empty block list', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    expect((await (await post(translationsUrl(), { locale: 'hu', blocks: [] })).json()).blocks).toEqual([]);
  });

  it('records the source revision it branched from', async () => {
    seedDocument(stub);
    stub.drafts.set(`${ORG_KEY}/${DOCUMENT_ID}/en`, aStoredDraft({ locale: 'en', revision: 7 }));
    await start();

    expect((await (await post(translationsUrl(), { locale: 'hu' })).json()).basedOnRevision).toBe(7);
  });

  it('rejects a locale that already has a translation', async () => {
    seedDocument(stub);
    await start();

    const response = await post(translationsUrl(), { locale: 'en' });
    expect(response.status).toBe(409);
    expect((await response.json()).errorId).toBe('document.translation.already-exists');
  });

  it.each([
    ['a missing locale', {}],
    ['a malformed locale', { locale: 'magyar' }],
  ])('rejects %s', async (_description, body) => {
    seedDocument(stub);
    await start();

    expect((await post(translationsUrl(), body)).status).toBe(400);
  });
});

describe('getDocumentTranslation', () => {
  it('returns the draft content', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    const response = await fetch(`${translationsUrl()}/en?draft=true`);
    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ locale: 'en', status: 'DRAFT', blocks: [aTextBlock('b1')] });
  });

  it('answers 404 for a locale with no translation', async () => {
    seedDocument(stub);
    await start();

    const response = await fetch(`${translationsUrl()}/de?draft=true`);
    expect(response.status).toBe(404);
    expect((await response.json()).errorId).toBe('document.translation.not-found');
  });

  it('answers 404 for a malformed locale', async () => {
    seedDocument(stub);
    await start();

    expect((await fetch(`${translationsUrl()}/magyar?draft=true`)).status).toBe(404);
  });

  it('answers 404 when the draft is not requested and the locale was never published', async () => {
    seedDocument(stub);
    await start();

    expect((await fetch(`${translationsUrl()}/en`)).status).toBe(404);
  });

  it('returns the published snapshot when the draft is not requested', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('draft-only')]);
    stub.published.set(`${ORG_KEY}/${DOCUMENT_ID}/en`, { locale: 'en', blocks: [aTextBlock('published')], publishedRevision: 1, publishedAt: '2026-02-01T00:00:00.000Z', publishedBy: null });
    await start();

    const translation = await (await fetch(`${translationsUrl()}/en`)).json();
    expect(translation.blocks).toEqual([aTextBlock('published')]);
    expect(translation.status).toBe('PUBLISHED');
  });
});

describe('removeDocumentTranslation', () => {
  it('removes the draft and its snapshot', async () => {
    seedDocument(stub);
    stub.drafts.set(`${ORG_KEY}/${DOCUMENT_ID}/hu`, aStoredDraft({ locale: 'hu' }));
    stub.published.set(`${ORG_KEY}/${DOCUMENT_ID}/hu`, { locale: 'hu', blocks: [], publishedRevision: 1, publishedAt: '2026-02-01T00:00:00.000Z', publishedBy: null });
    await start();

    expect((await fetch(`${translationsUrl()}/hu`, { method: 'DELETE' })).status).toBe(204);
    expect(stub.drafts.has(`${ORG_KEY}/${DOCUMENT_ID}/hu`)).toBe(false);
    expect(stub.published.has(`${ORG_KEY}/${DOCUMENT_ID}/hu`)).toBe(false);
  });

  it('refuses to remove the source locale', async () => {
    seedDocument(stub);
    await start();

    const response = await fetch(`${translationsUrl()}/en`, { method: 'DELETE' });
    expect(response.status).toBe(409);
    expect((await response.json()).errorId).toBe('document.translation.source-locale-not-removable');
  });

  it('answers 404 for a locale with no translation', async () => {
    seedDocument(stub);
    await start();

    expect((await fetch(`${translationsUrl()}/de`, { method: 'DELETE' })).status).toBe(404);
  });
});

describe('appendDocumentBlock', () => {
  it('answers 201 with a server-assigned id and bumps the revision', async () => {
    seedDocument(stub);
    await start();

    const response = await post(blocksUrl(), aTextBlockInput);
    expect(response.status).toBe(201);

    const block = await response.json();
    expect(block.id).toBeTruthy();
    expect(stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`)).toMatchObject({ revision: 2, blocks: [block] });
  });

  it('ignores an id supplied in the body', async () => {
    seedDocument(stub);
    await start();

    const block = await (await post(blocksUrl(), { ...aTextBlockInput, id: 'client-chosen' })).json();
    expect(block.id).not.toBe('client-chosen');
  });

  it('appends after the existing blocks', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    await post(blocksUrl(), aTextBlockInput);
    expect(stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`)?.blocks[0].id).toBe('b1');
  });

  it('accepts a widget block', async () => {
    seedDocument(stub);
    await start();

    const block = await (await post(blocksUrl(), { kind: 'WIDGET', placement: 'STANDALONE', type: 'entity-table', props: { entity: 'order' } })).json();
    expect(block).toMatchObject({ kind: 'WIDGET', placement: 'STANDALONE', type: 'entity-table' });
  });

  it.each([
    ['a missing kind', {}],
    ['an unknown kind', { kind: 'PICTURE' }],
  ])('rejects %s', async (_description, body) => {
    seedDocument(stub);
    await start();

    const response = await post(blocksUrl(), body);
    expect(response.status).toBe(400);
    expect((await response.json()).errorId).toBe('document.input.invalid');
  });

  it('answers 404 when the locale has no draft', async () => {
    seedDocument(stub);
    await start();

    expect((await post(blocksUrl('de'), aTextBlockInput)).status).toBe(404);
  });

  it('answers 404 for a malformed locale', async () => {
    seedDocument(stub);
    await start();

    expect((await post(blocksUrl('magyar'), aTextBlockInput)).status).toBe(404);
  });
});

describe('replaceDocumentBlock', () => {
  it('replaces in place and answers 200', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1', 'before'), aTextBlock('b2')]);
    await start();

    const response = await put(`${blocksUrl()}/b1`, { kind: 'TEXT', content: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: 'after' }] }] } });
    expect(response.status).toBe(200);

    const draft = stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`);
    expect(draft?.blocks.map((block) => block.id)).toEqual(['b1', 'b2']);
    expect(JSON.stringify(draft?.blocks[0].content)).toContain('after');
    expect(draft?.revision).toBe(2);
  });

  it('takes the id from the path, ignoring the body', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    const block = await (await put(`${blocksUrl()}/b1`, { ...aTextBlockInput, id: 'client-chosen' })).json();
    expect(block.id).toBe('b1');
  });

  it('answers 404 for a block that is not in the translation', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    const response = await put(`${blocksUrl()}/unknown`, aTextBlockInput);
    expect(response.status).toBe(404);
    expect((await response.json()).errorId).toBe('document.block.not-found');
  });

  it('rejects a body with no kind', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    expect((await put(`${blocksUrl()}/b1`, {})).status).toBe(400);
  });
});

describe('deleteDocumentBlock', () => {
  it('removes the block and answers 204', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1'), aTextBlock('b2')]);
    await start();

    expect((await fetch(`${blocksUrl()}/b1`, { method: 'DELETE' })).status).toBe(204);
    expect(stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`)?.blocks.map((block) => block.id)).toEqual(['b2']);
  });

  it('refuses to delete a block another block still embeds, and names the referrers', async () => {
    seedDocument(stub, aStoredDocument(), [aWidgetBlock('widget-1'), anEmbeddingBlock('text-1', 'widget-1')]);
    await start();

    const response = await fetch(`${blocksUrl()}/widget-1`, { method: 'DELETE' });
    expect(response.status).toBe(409);

    const body = await response.json();
    expect(body.errorId).toBe('document.block.referenced');
    // The referrers are named in `errorText`, the only place the contract gives them: `ErrorResponse`
    // is exactly these two keys, and the wording matches DocumentBlockReferencedException's message.
    expect(body.errorText).toBe("Block 'widget-1' is still referenced by: [text-1]");
    expect(Object.keys(body).sort()).toEqual(['errorId', 'errorText']);
    expect(stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`)?.blocks).toHaveLength(2);
  });

  it('answers 404 for a block that is not in the translation', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    expect((await fetch(`${blocksUrl()}/unknown`, { method: 'DELETE' })).status).toBe(404);
  });
});

describe('reorderDocumentBlocks', () => {
  it('applies the new order and returns the reordered list', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1'), aTextBlock('b2'), aTextBlock('b3')]);
    await start();

    const response = await put(`${blocksUrl()}/reorder`, { blockIds: ['b3', 'b1', 'b2'] });
    expect(response.status).toBe(200);
    expect((await response.json()).map((block: { id: string }) => block.id)).toEqual(['b3', 'b1', 'b2']);
    expect(stub.drafts.get(`${ORG_KEY}/${DOCUMENT_ID}/en`)?.blocks.map((block) => block.id)).toEqual(['b3', 'b1', 'b2']);
  });

  it('is routed ahead of the block id route, so `reorder` is not read as an id', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    expect((await put(`${blocksUrl()}/reorder`, { blockIds: ['b1'] })).status).toBe(200);
  });

  it.each([
    ['a subset, which would silently drop the rest', ['b1']],
    ['an unknown id', ['b1', 'b2', 'unknown']],
    ['a duplicate that pads the length', ['b1', 'b1']],
  ])('rejects %s', async (_description, blockIds) => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1'), aTextBlock('b2')]);
    await start();

    const response = await put(`${blocksUrl()}/reorder`, { blockIds });
    expect(response.status).toBe(400);
    expect((await response.json()).errorId).toBe('document.blocks.not-a-permutation');
  });

  it.each([
    ['a missing list', {}],
    ['a non-array', { blockIds: 'b1' }],
    ['non-string entries', { blockIds: [1, 2] }],
  ])('rejects %s', async (_description, body) => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    await start();

    const response = await put(`${blocksUrl()}/reorder`, body);
    expect(response.status).toBe(400);
    expect((await response.json()).errorId).toBe('document.input.invalid');
  });
});

describe('access policy', () => {
  const policy = (overrides: Partial<DocumentAccessPolicy>): DocumentAccessPolicy => ({
    mayAccessOrganization: () => true,
    mayRead: () => true,
    mayEdit: () => true,
    principalOf: () => null,
    ...overrides,
  });

  it('answers 403 when the organization is closed to the caller', async () => {
    await start(policy({ mayAccessOrganization: () => false }));

    const response = await fetch(documentsUrl());
    expect(response.status).toBe(403);
    expect((await response.json()).errorId).toBe('document.organization.access-denied');
  });

  it('answers 403 when the organization is closed to a create', async () => {
    await start(policy({ mayAccessOrganization: () => false }));

    expect((await post(documentsUrl(), validProperties)).status).toBe(403);
  });

  it('answers 404 rather than 403 on read denial, so ids cannot be probed', async () => {
    seedDocument(stub);
    await start(policy({ mayRead: () => false }));

    const response = await fetch(documentUrl());
    expect(response.status).toBe(404);
    expect((await response.json()).errorId).toBe('document.not-found');
  });

  it('hides unreadable documents from the list', async () => {
    seedDocument(stub);
    await start(policy({ mayRead: () => false }));

    expect((await (await fetch(documentsUrl())).json()).totalElements).toBe(0);
  });

  it('answers 403 on edit denial, because the caller has already been shown it exists', async () => {
    seedDocument(stub);
    await start(policy({ mayEdit: () => false }));

    const response = await put(`${documentUrl()}/properties`, validProperties);
    expect(response.status).toBe(403);
    expect((await response.json()).errorId).toBe('document.access-denied');
  });

  it('stamps the principal it resolves into createdBy', async () => {
    await start(policy({ principalOf: () => 'alice@example.com' }));

    const document = await (await post(documentsUrl(), validProperties)).json();
    expect(document.createdBy).toBe('alice@example.com');
  });
});
