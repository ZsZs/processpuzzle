import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';
import type { RunningApp, StorageStub } from './test-support.js';

vi.mock('firebase-functions', () => ({ logger: { info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const { ObjectStoreHandlers } = await import('./object-store.handlers.js');
const { createObjectStoreApp } = await import('./object-store.function.js');
const { createStorageStub, serve, uploadForm } = await import('./test-support.js');

const API_PATH = join(__dirname, '../../../../../libs/java-shared/api-contracts/src/main/resources/processpuzzle-store-api.yaml');

interface OpenApiOperation {
  operationId: string;
  responses: Record<string, { headers?: Record<string, unknown>; content?: Record<string, { schema?: Record<string, unknown> }> }>;
}

type OpenApiDocument = {
  paths: Record<string, Record<string, OpenApiOperation>>;
  components: { schemas: Record<string, { required?: string[]; properties?: Record<string, unknown> }> };
};

const api = parse(readFileSync(API_PATH, 'utf8')) as OpenApiDocument;

function operation(path: string, method: string): OpenApiOperation {
  const found = api.paths[path]?.[method];
  if (!found) throw new Error(`${method.toUpperCase()} ${path} is not declared in processpuzzle-store-api.yaml`);
  return found;
}

function declaredStatuses(path: string, method: string): string[] {
  return Object.keys(operation(path, method).responses);
}

let storage: StorageStub;
let app: RunningApp;

async function uploadPdf(): Promise<string> {
  const response = await fetch(`${app.base}/objects`, { method: 'POST', body: uploadForm(Buffer.from('%PDF-1.4'), 'doc.pdf', 'application/pdf') });
  return (await response.json()).objectID;
}

beforeEach(async () => {
  vi.clearAllMocks();
  storage = createStorageStub();
  app = await serve(createObjectStoreApp(new ObjectStoreHandlers(storage.service)));
});

afterEach(async () => {
  await app.close();
});

/**
 * processpuzzle-store-api.yaml is the only thing that makes the MinIO and Firebase
 * implementations interchangeable — ObjectStoreService is written against the contract,
 * not against either backend. These tests assert this implementation still answers what
 * the contract promises, so a drift shows up here rather than in a deployed app.
 */
describe('every declared operation is routed', () => {
  // Express answers an unrouted path with an HTML 404; our handlers always answer JSON.
  // A JSON body therefore proves the operation is actually served.
  it.each([
    ['/objects', 'post'],
    ['/objects/{bucketName}/{objectID}', 'get'],
    ['/objects/{bucketName}/{objectID}', 'delete'],
    ['/objects/{bucketName}/{objectID}/uri', 'get'],
    ['/objects/{bucketName}/{objectID}/thumbnail-uri', 'get'],
  ])('serves %s %s', async (path, method) => {
    expect(operation(path, method).operationId).toBeTruthy();

    const url = app.base + path.replace('{bucketName}', 'documents').replace('{objectID}', 'absent');
    const response = await fetch(url, { method: method.toUpperCase(), body: method === 'post' ? new FormData() : undefined });

    expect(response.headers.get('content-type')).toContain('application/json');
  });

  it('declares no operation this implementation does not serve', () => {
    const declared = Object.entries(api.paths).flatMap(([path, methods]) => Object.keys(methods).map((method) => `${method} ${path}`));

    expect(declared.sort()).toEqual(
      [
        'post /objects',
        'get /objects/{bucketName}/{objectID}',
        'delete /objects/{bucketName}/{objectID}',
        'get /objects/{bucketName}/{objectID}/uri',
        'get /objects/{bucketName}/{objectID}/thumbnail-uri',
      ].sort(),
    );
  });
});

describe('uploadObject', () => {
  it('answers a declared status', async () => {
    const response = await fetch(`${app.base}/objects`, { method: 'POST', body: uploadForm(Buffer.from('x'), 'doc.pdf', 'application/pdf') });

    expect(declaredStatuses('/objects', 'post')).toContain(String(response.status));
  });

  it('sends the Location header the contract declares', async () => {
    const response = await fetch(`${app.base}/objects`, { method: 'POST', body: uploadForm(Buffer.from('x'), 'doc.pdf', 'application/pdf') });

    expect(Object.keys(operation('/objects', 'post').responses['201'].headers ?? {})).toContain('Location');
    expect(response.headers.get('Location')).toBeTruthy();
  });

  it('answers the 201 body shape the contract declares', async () => {
    const response = await fetch(`${app.base}/objects`, { method: 'POST', body: uploadForm(Buffer.from('x'), 'doc.pdf', 'application/pdf') });
    const schema = operation('/objects', 'post').responses['201'].content?.['application/json']?.schema as { properties: Record<string, unknown> };

    expect(Object.keys(await response.json()).sort()).toEqual(Object.keys(schema.properties).sort());
  });

  it('answers a declared status for an invalid request', async () => {
    const response = await fetch(`${app.base}/objects`, { method: 'POST', body: new FormData() });

    expect(declaredStatuses('/objects', 'post')).toContain(String(response.status));
    expect(Object.keys(await response.json()).sort()).toEqual(api.components.schemas['ErrorResponse'].required?.slice().sort());
  });
});

describe('getObjectByID', () => {
  it('sends the object headers the contract declares', async () => {
    const objectID = await uploadPdf();
    const declaredHeaders = Object.keys(operation('/objects/{bucketName}/{objectID}', 'get').responses['200'].headers ?? {});

    const response = await fetch(`${app.base}/objects/documents/${objectID}`);

    expect(declaredHeaders).toEqual(['X-Object-Name', 'X-Object-Bucket']);
    for (const header of declaredHeaders) expect(response.headers.get(header)).toBeTruthy();
  });

  it('answers a declared status when the object is absent', async () => {
    const response = await fetch(`${app.base}/objects/documents/absent`);

    expect(declaredStatuses('/objects/{bucketName}/{objectID}', 'get')).toContain(String(response.status));
  });
});

describe('uri operations', () => {
  it.each(['/objects/{bucketName}/{objectID}/uri', '/objects/{bucketName}/{objectID}/thumbnail-uri'])('%s answers an ObjectUriResponse', async (path) => {
    const schemaRef = operation(path, 'get').responses['200'].content?.['application/json']?.schema as { $ref: string };
    expect(schemaRef.$ref).toBe('#/components/schemas/ObjectUriResponse');

    // Only the plain object has a thumbnail-free counterpart, so seed an image for both.
    const created = await fetch(`${app.base}/objects`, { method: 'POST', body: uploadForm(Buffer.from('%PDF'), 'doc.pdf', 'application/pdf') });
    const { objectID } = await created.json();
    const isThumbnail = path.endsWith('thumbnail-uri');
    if (isThumbnail) storage.entries.set(`documents/${objectID}-thumb`, { content: Buffer.from('t'), contentType: 'image/jpeg', metadata: {} });

    const response = await fetch(`${app.base}${path.replace('{bucketName}', 'documents').replace('{objectID}', objectID)}`);

    expect(response.status).toBe(200);
    expect(Object.keys(await response.json())).toEqual(api.components.schemas['ObjectUriResponse'].required);
  });
});

describe('deleteObjectByID', () => {
  it('answers the declared no-content status', async () => {
    const objectID = await uploadPdf();

    const response = await fetch(`${app.base}/objects/documents/${objectID}`, { method: 'DELETE' });

    expect(response.status).toBe(204);
    expect(declaredStatuses('/objects/{bucketName}/{objectID}', 'delete')).toContain('204');
  });
});
