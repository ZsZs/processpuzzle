import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { RunningApp, StorageStub } from './test-support.js';

vi.mock('firebase-functions', () => ({ logger: { info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));

const { logger } = await import('firebase-functions');
const { ObjectStoreHandlers } = await import('./object-store.handlers.js');
const { createObjectStoreApp } = await import('./object-store.function.js');
const { createStorageStub, serve, uploadForm } = await import('./test-support.js');

let storage: StorageStub;
let app: RunningApp;

beforeEach(async () => {
  vi.clearAllMocks();
  storage = createStorageStub();
  app = await serve(createObjectStoreApp(new ObjectStoreHandlers(storage.service)));
});

afterEach(async () => {
  await app.close();
});

describe('mount points', () => {
  // Behind the Hosting rewrite the function sees /api/store/...; called directly by its
  // function URL it sees the bare path. Both have to work off the same deployment.
  it.each(['/api/store', ''])('serves the API under "%s"', async (prefix) => {
    const created = await fetch(`${app.base}${prefix}/objects`, { method: 'POST', body: uploadForm(Buffer.from('x'), 'a.txt', 'text/plain') });
    const { objectID } = await created.json();

    expect(created.status).toBe(201);
    expect((await fetch(`${app.base}${prefix}/objects/documents/${objectID}/uri`)).status).toBe(200);
  });
});

describe('CORS', () => {
  it('exposes the headers the frontend reads off the response', async () => {
    const response = await fetch(`${app.base}/objects/documents/absent/uri`, { headers: { Origin: 'http://localhost:4200' } });
    const exposed = response.headers.get('access-control-expose-headers')?.split(',');

    expect(exposed).toEqual(['Location', 'X-Object-Name', 'X-Object-Bucket']);
  });

  it('reflects the requesting origin', async () => {
    const response = await fetch(`${app.base}/objects/documents/absent/uri`, { headers: { Origin: 'http://localhost:4200' } });

    expect(response.headers.get('access-control-allow-origin')).toBe('http://localhost:4200');
  });

  it('answers a preflight for the upload', async () => {
    const response = await fetch(`${app.base}/objects`, {
      method: 'OPTIONS',
      headers: { Origin: 'http://localhost:4200', 'Access-Control-Request-Method': 'POST' },
    });

    expect(response.status).toBeLessThan(300);
    expect(response.headers.get('access-control-allow-origin')).toBe('http://localhost:4200');
  });
});

describe('error handling', () => {
  it('turns an unexpected storage failure into a 500 ErrorResponse', async () => {
    storage.failWith(new Error('storage is down'));

    const response = await fetch(`${app.base}/objects/documents/obj-1/uri`);

    expect(response.status).toBe(500);
    expect(await response.json()).toEqual({ errorId: 'store.internal-error', errorText: 'storage is down' });
  });

  it('logs the failure it reports', async () => {
    storage.failWith(new Error('storage is down'));

    await fetch(`${app.base}/objects/documents/obj-1/uri`);

    expect(logger.error).toHaveBeenCalledWith('Object store request failed', expect.any(Error));
  });
});
