import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';
import type { DocumentStoreStub, RunningApp } from './test-support.js';

vi.mock('firebase-functions', () => ({ logger: { info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const { BaseDocumentHandlers } = await import('./base-document.handlers.js');
const { createBaseDocumentApp } = await import('./base-document.function.js');
const { aStoredDocument, aTextBlock, createDocumentStoreStub, ORG_KEY, seedDocument, serve } = await import('./test-support.js');

const API_PATH = join(__dirname, '../../../../../libs/java-shared/api-contracts/src/main/resources/base-document-api.yaml');

interface SchemaNode {
  $ref?: string;
  allOf?: SchemaNode[];
  properties?: Record<string, unknown>;
  required?: string[];
}

interface OpenApiOperation {
  operationId: string;
  parameters?: SchemaNode[];
  responses: Record<string, { headers?: Record<string, unknown>; content?: Record<string, { schema?: SchemaNode }> }>;
}

type OpenApiDocument = {
  paths: Record<string, Record<string, OpenApiOperation>>;
  components: { parameters: Record<string, { name: string; schema?: { default?: unknown } }>; schemas: Record<string, SchemaNode> };
};

const api = parse(readFileSync(API_PATH, 'utf8')) as OpenApiDocument;

/**
 * The fourteen operations this function serves. Together with `DEFERRED` this list must account for
 * every operation the contract declares — the assertion below is what turns "we quietly skipped one"
 * and "the contract grew an operation" into a failing build rather than a 404 in production.
 */
const IMPLEMENTED: ReadonlyArray<readonly [string, string]> = [
  ['/organizations/{orgKey}/documents', 'get'],
  ['/organizations/{orgKey}/documents', 'post'],
  ['/organizations/{orgKey}/documents/{documentId}', 'get'],
  ['/organizations/{orgKey}/documents/{documentId}', 'put'],
  ['/organizations/{orgKey}/documents/{documentId}', 'delete'],
  ['/organizations/{orgKey}/documents/{documentId}/properties', 'put'],
  ['/organizations/{orgKey}/documents/{documentId}/translations', 'get'],
  ['/organizations/{orgKey}/documents/{documentId}/translations', 'post'],
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}', 'get'],
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}', 'delete'],
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks', 'post'],
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks/reorder', 'put'],
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks/{blockId}', 'put'],
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks/{blockId}', 'delete'],
];

/**
 * Declared but not served yet. Publishing needs the draft-vs-snapshot revision model, and
 * validate/import/export need the full referential-integrity checker and YAML handling. Promoting one
 * means moving a line from here to `IMPLEMENTED` — at which point the routing assertion starts
 * demanding it.
 */
const DEFERRED: ReadonlyArray<readonly [string, string]> = [
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}/publish', 'post'],
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}/unpublish', 'post'],
  ['/organizations/{orgKey}/documents/{documentId}/translations/{locale}/discard-draft', 'post'],
  ['/organizations/{orgKey}/content/{slug}', 'get'],
  ['/organizations/{orgKey}/documents/validate', 'post'],
  ['/organizations/{orgKey}/documents/import', 'post'],
  ['/organizations/{orgKey}/documents/{documentId}/export', 'get'],
  ['/organizations/{orgKey}/document/translations/{locale}', 'get'],
  ['/organizations/{orgKey}/document/translations/{scope}/{locale}', 'get'],
];

const DOCUMENT_ID = '11111111-1111-1111-1111-111111111111';
const ABSENT_ID = '99999999-9999-9999-9999-999999999999';

/**
 * Every path in this contract carries a path-level `parameters` key alongside its verbs, so the keys
 * of a path item are not all operations — unlike `processpuzzle-store-api.yaml`, whose contract spec
 * can read them directly.
 */
const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options', 'trace'];

function declaredOperations(): string[] {
  return Object.entries(api.paths).flatMap(([path, methods]) =>
    Object.keys(methods)
      .filter((method) => HTTP_METHODS.includes(method))
      .map((method) => `${method} ${path}`),
  );
}

function operation(path: string, method: string): OpenApiOperation {
  const found = api.paths[path]?.[method];
  if (!found) throw new Error(`${method.toUpperCase()} ${path} is not declared in base-document-api.yaml`);
  return found;
}

function declaredStatuses(path: string, method: string): string[] {
  return Object.keys(operation(path, method).responses);
}

/** Flattens `allOf` and `$ref` so a composed response schema can be compared against a real body. */
function propertiesOf(schema: SchemaNode | undefined): string[] {
  if (!schema) return [];
  if (schema.$ref) return propertiesOf(api.components.schemas[schema.$ref.replace('#/components/schemas/', '')]);

  const own = Object.keys(schema.properties ?? {});
  const composed = (schema.allOf ?? []).flatMap((branch) => propertiesOf(branch));
  return [...new Set([...own, ...composed])];
}

function responseSchema(path: string, method: string, status: string): SchemaNode | undefined {
  return operation(path, method).responses[status]?.content?.['application/json']?.schema;
}

function parameterDefault(name: string): unknown {
  return Object.values(api.components.parameters).find((parameter) => parameter.name === name)?.schema?.default;
}

function url(path: string, { documentId = DOCUMENT_ID, locale = 'en', blockId = 'b1' } = {}): string {
  return app.base + path.replace('{orgKey}', ORG_KEY).replace('{documentId}', documentId).replace('{locale}', locale).replace('{blockId}', blockId).replace('{slug}', 'q3-plan');
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const validProperties = { slug: 'q3-plan', title: 'Q3 plan', sourceLocale: 'en' };

let stub: DocumentStoreStub;
let app: RunningApp;

beforeEach(async () => {
  vi.clearAllMocks();
  stub = createDocumentStoreStub();
  app = await serve(createBaseDocumentApp(new BaseDocumentHandlers(stub.store)));
});

afterEach(async () => {
  await app.close();
});

/**
 * base-document-api.yaml is the only thing that makes the JPA and Firestore implementations
 * interchangeable — the Java DTOs are generated from it, this function is written against it, and
 * `BaseDocumentService` talks to whichever one the deployment binds. These tests assert this
 * implementation still answers what the contract promises, so a drift shows up here rather than in a
 * deployed app.
 */
describe('operation coverage', () => {
  it('accounts for every declared operation as either implemented or deliberately deferred', () => {
    const accounted = [...IMPLEMENTED, ...DEFERRED].map(([path, method]) => `${method} ${path}`);

    expect(declaredOperations().sort()).toEqual(accounted.sort());
  });

  it('claims no operation as both implemented and deferred', () => {
    const implemented = IMPLEMENTED.map(([path, method]) => `${method} ${path}`);
    const deferred = DEFERRED.map(([path, method]) => `${method} ${path}`);

    expect(implemented.filter((entry) => deferred.includes(entry))).toEqual([]);
  });

  // Express answers an unrouted path with an HTML 404; our handlers always answer JSON.
  // A JSON body therefore proves the operation is actually served.
  it.each(IMPLEMENTED)('serves %s %s', async (path, method) => {
    expect(operation(path, method).operationId).toBeTruthy();

    const response = await fetch(url(path, { documentId: ABSENT_ID }), { method: method.toUpperCase(), headers: JSON_HEADERS, body: method === 'get' || method === 'delete' ? undefined : '{}' });

    expect(response.headers.get('content-type')).toContain('application/json');
  });

  it.each(DEFERRED)('does not pretend to serve %s %s', async (path, method) => {
    const response = await fetch(url(path), { method: method.toUpperCase(), headers: JSON_HEADERS, body: method === 'get' ? undefined : '{}' });

    // A deferred operation must be visibly absent rather than answered wrongly.
    expect(response.headers.get('content-type')).not.toContain('application/json');
    expect(response.status).toBe(404);
  });
});

describe('declared statuses', () => {
  it.each(IMPLEMENTED)('%s %s answers a declared status when the document is absent', async (path, method) => {
    const response = await fetch(url(path, { documentId: ABSENT_ID }), { method: method.toUpperCase(), headers: JSON_HEADERS, body: method === 'get' || method === 'delete' ? undefined : '{}' });

    expect(declaredStatuses(path, method)).toContain(String(response.status));
  });

  it.each(IMPLEMENTED)('%s %s answers a declared status against a real document', async (path, method) => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    const bodies: Record<string, unknown> = {
      'put /organizations/{orgKey}/documents/{documentId}': validProperties,
      'put /organizations/{orgKey}/documents/{documentId}/properties': validProperties,
      'post /organizations/{orgKey}/documents/{documentId}/translations': { locale: 'hu' },
      'post /organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks': { kind: 'TEXT' },
      'put /organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks/reorder': { blockIds: ['b1'] },
      'put /organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks/{blockId}': { kind: 'TEXT' },
      'post /organizations/{orgKey}/documents': { ...validProperties, slug: 'another-plan' },
    };
    const body = bodies[`${method} ${path}`];

    const response = await fetch(url(path) + (method === 'get' ? '?draft=true' : ''), {
      method: method.toUpperCase(),
      headers: JSON_HEADERS,
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    expect(declaredStatuses(path, method)).toContain(String(response.status));
  });
});

describe('response bodies', () => {
  it('listDocuments answers the declared PageOf_DocumentSummary shape', async () => {
    seedDocument(stub);

    const body = await (await fetch(url('/organizations/{orgKey}/documents'))).json();

    expect(Object.keys(body).sort()).toEqual(propertiesOf(responseSchema('/organizations/{orgKey}/documents', 'get', '200')).sort());
  });

  it('its content entries answer the declared DocumentSummary shape', async () => {
    seedDocument(stub);

    const body = await (await fetch(url('/organizations/{orgKey}/documents'))).json();

    expect(Object.keys(body.content[0]).sort()).toEqual(propertiesOf({ $ref: '#/components/schemas/DocumentSummary' }).sort());
  });

  it('createDocument answers the declared Document shape', async () => {
    const response = await fetch(url('/organizations/{orgKey}/documents'), { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(validProperties) });

    expect(response.status).toBe(201);
    expect(Object.keys(await response.json()).sort()).toEqual(propertiesOf(responseSchema('/organizations/{orgKey}/documents', 'post', '201')).sort());
  });

  it('getDocumentTranslation answers the declared DocumentTranslation shape', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);

    const response = await fetch(`${url('/organizations/{orgKey}/documents/{documentId}/translations/{locale}')}?draft=true`);

    expect(Object.keys(await response.json()).sort()).toEqual(propertiesOf(responseSchema('/organizations/{orgKey}/documents/{documentId}/translations/{locale}', 'get', '200')).sort());
  });

  it('listDocumentTranslations answers an array of the declared DocumentTranslationSummary shape', async () => {
    seedDocument(stub);
    const schema = responseSchema('/organizations/{orgKey}/documents/{documentId}/translations', 'get', '200') as SchemaNode & { items?: SchemaNode };

    const body = await (await fetch(url('/organizations/{orgKey}/documents/{documentId}/translations'))).json();

    expect(Array.isArray(body)).toBe(true);
    expect(Object.keys(body[0]).sort()).toEqual(propertiesOf(schema.items).sort());
  });

  it('appendDocumentBlock answers the declared DocumentBlock shape', async () => {
    seedDocument(stub);
    const declared = propertiesOf(responseSchema('/organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks', 'post', '201'));

    const response = await fetch(url('/organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks'), {
      method: 'POST',
      headers: JSON_HEADERS,
      body: JSON.stringify({ kind: 'TEXT', editable: true, content: { type: 'doc' }, placement: 'STANDALONE', type: 'x', props: {}, inputBindings: {}, outputBindings: {} }),
    });

    // Optionals are omitted rather than sent as null, so the body is a subset of the declared shape.
    expect(response.status).toBe(201);
    expect(Object.keys(await response.json()).every((key) => declared.includes(key))).toBe(true);
  });

  it('answers the declared ErrorResponse shape for a 404', async () => {
    const response = await fetch(url('/organizations/{orgKey}/documents/{documentId}', { documentId: ABSENT_ID }));

    expect(response.status).toBe(404);
    expect(Object.keys(await response.json()).sort()).toEqual(api.components.schemas['ErrorResponse'].required?.slice().sort());
  });

  it('answers the declared ErrorResponse shape for a 400', async () => {
    const response = await fetch(url('/organizations/{orgKey}/documents'), { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify({ title: 'No slug' }) });

    expect(response.status).toBe(400);
    expect(Object.keys(await response.json()).sort()).toEqual(api.components.schemas['ErrorResponse'].required?.slice().sort());
  });

  it('answers the declared ErrorResponse shape for a 409', async () => {
    seedDocument(stub);

    const response = await fetch(url('/organizations/{orgKey}/documents'), { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(validProperties) });

    expect(response.status).toBe(409);
    expect(Object.keys(await response.json()).sort()).toEqual(api.components.schemas['ErrorResponse'].required?.slice().sort());
  });

  it('answers the declared no-content status for the two delete operations', async () => {
    seedDocument(stub, aStoredDocument(), [aTextBlock('b1')]);
    stub.drafts.set(`${ORG_KEY}/${DOCUMENT_ID}/hu`, { locale: 'hu', blocks: [], revision: 1, basedOnRevision: 1, createdAt: '2026-01-01T00:00:00.000Z', updatedAt: '2026-01-01T00:00:00.000Z' });

    const block = await fetch(url('/organizations/{orgKey}/documents/{documentId}/translations/{locale}/blocks/{blockId}'), { method: 'DELETE' });
    const translation = await fetch(url('/organizations/{orgKey}/documents/{documentId}/translations/{locale}', { locale: 'hu' }), { method: 'DELETE' });
    const document = await fetch(url('/organizations/{orgKey}/documents/{documentId}'), { method: 'DELETE' });

    for (const response of [block, translation, document]) expect(response.status).toBe(204);
    expect(declaredStatuses('/organizations/{orgKey}/documents/{documentId}', 'delete')).toContain('204');
  });
});

describe('declared parameter defaults', () => {
  it('applies the declared page and size defaults', async () => {
    seedDocument(stub);
    expect(parameterDefault('page')).toBe(0);
    expect(parameterDefault('size')).toBe(20);

    const body = await (await fetch(url('/organizations/{orgKey}/documents'))).json();

    expect(body.number).toBe(0);
    expect(body.size).toBe(20);
  });

  it('treats draft as false by default, as the contract declares', async () => {
    seedDocument(stub);
    expect(parameterDefault('draft')).toBe(false);

    // Nothing is published, so the default must not fall back to the draft.
    const response = await fetch(url('/organizations/{orgKey}/documents/{documentId}/translations/{locale}'));

    expect(response.status).toBe(404);
    expect(declaredStatuses('/organizations/{orgKey}/documents/{documentId}/translations/{locale}', 'get')).toContain('404');
  });
});
