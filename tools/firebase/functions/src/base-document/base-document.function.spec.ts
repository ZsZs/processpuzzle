import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { DocumentStoreStub, RunningApp } from './test-support.js';

vi.mock('firebase-functions', () => ({ logger: { info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const { logger } = await import('firebase-functions');
const { BaseDocumentHandlers } = await import('./base-document.handlers.js');
const { createBaseDocumentApp, createBaseDocumentRouter } = await import('./base-document.function.js');
const { createDocumentStoreStub, ORG_KEY, seedDocument, serve } = await import('./test-support.js');

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

describe('createBaseDocumentApp', () => {
  it('serves the bare contract path, as the emulator function URL and the specs call it', async () => {
    seedDocument(stub);

    expect((await fetch(`${app.base}/organizations/${ORG_KEY}/documents`)).status).toBe(200);
  });

  it('serves the same routes under the /api prefix the Hosting rewrite delivers', async () => {
    seedDocument(stub);

    expect((await fetch(`${app.base}/api/organizations/${ORG_KEY}/documents`)).status).toBe(200);
  });

  it('answers an unknown path with express own 404 rather than a contract error', async () => {
    const response = await fetch(`${app.base}/organizations/${ORG_KEY}/nonsense`);

    expect(response.status).toBe(404);
    expect(response.headers.get('content-type')).not.toContain('application/json');
  });

  it('turns an unexpected failure into a 500 ErrorResponse and logs it', async () => {
    stub.failWith(new Error('Firestore is unreachable'));

    const response = await fetch(`${app.base}/organizations/${ORG_KEY}/documents`);
    expect(response.status).toBe(500);
    expect(await response.json()).toEqual({ errorId: 'document.internal-error', errorText: 'Firestore is unreachable' });
    expect(logger.error).toHaveBeenCalledWith('Base document request failed', expect.any(Error));
  });

  it('answers a malformed JSON body with 400, not the 500 express would default to', async () => {
    const response = await fetch(`${app.base}/organizations/${ORG_KEY}/documents`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{ "slug": ' });

    expect(response.status).toBe(400);
    expect((await response.json()).errorId).toBe('document.input.malformed-json');
  });

  it('allows the Access-Control-* request headers BaseEntityRestService sends, which would fail preflight', async () => {
    const response = await fetch(`${app.base}/organizations/${ORG_KEY}/documents`, {
      method: 'OPTIONS',
      headers: { Origin: 'https://processpuzzle-testbed-stage.firebaseapp.com', 'Access-Control-Request-Method': 'PUT', 'Access-Control-Request-Headers': 'access-control-allow-origin,content-type' },
    });

    expect(response.status).toBeLessThan(300);
    const allowed = response.headers.get('access-control-allow-headers') ?? '';
    expect(allowed.toLowerCase()).toContain('access-control-allow-origin');
    expect(allowed.toLowerCase()).toContain('content-type');
  });

  it('reflects the request origin so a browser accepts the response', async () => {
    const response = await fetch(`${app.base}/organizations/${ORG_KEY}/documents`, { headers: { Origin: 'https://processpuzzle-testbed-stage.firebaseapp.com' } });

    expect(response.headers.get('access-control-allow-origin')).toBe('https://processpuzzle-testbed-stage.firebaseapp.com');
  });

  it('accepts a body larger than the 100 kB express default, because Tiptap content is the payload', async () => {
    seedDocument(stub);
    const longText = 'x'.repeat(200_000);
    const body = JSON.stringify({ kind: 'TEXT', content: { type: 'doc', content: [{ type: 'paragraph', content: [{ type: 'text', text: longText }] }] } });

    const response = await fetch(`${app.base}/organizations/${ORG_KEY}/documents/11111111-1111-1111-1111-111111111111/translations/en/blocks`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
    });

    expect(response.status).toBe(201);
  });
});

describe('createBaseDocumentRouter', () => {
  it('constructs its own handlers when none are injected, which is how the deployed function is built', () => {
    expect(() => createBaseDocumentRouter()).not.toThrow();
  });
});
