import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import sharp from 'sharp';
import type { RunningApp, StorageStub } from './test-support.js';

vi.mock('firebase-functions', () => ({ logger: { info: vi.fn(), warn: vi.fn(), error: vi.fn() } }));
vi.mock('./multipart.js', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./multipart.js')>();
  return { ...actual, parseMultipart: vi.fn(actual.parseMultipart) };
});

const { parseMultipart, PayloadTooLargeError } = await import('./multipart.js');
const { ObjectStoreHandlers } = await import('./object-store.handlers.js');
const { createObjectStoreApp } = await import('./object-store.function.js');
const { createStorageStub, serve, uploadForm } = await import('./test-support.js');

let storage: StorageStub;
let app: RunningApp;

async function png(width = 320, height = 240): Promise<Buffer> {
  return sharp({ create: { width, height, channels: 3, background: '#884422' } })
    .png()
    .toBuffer();
}

async function upload(content: Buffer, fileName: string, mimeType: string): Promise<Response> {
  return fetch(`${app.base}/objects`, { method: 'POST', body: uploadForm(content, fileName, mimeType) });
}

beforeEach(async () => {
  vi.clearAllMocks();
  storage = createStorageStub();
  app = await serve(createObjectStoreApp(new ObjectStoreHandlers(storage.service)));
});

afterEach(async () => {
  await app.close();
});

describe('uploadObject', () => {
  it('stores the object and answers 201 with its identity', async () => {
    const response = await upload(Buffer.from('%PDF-1.4'), 'doc.pdf', 'application/pdf');
    const body = await response.json();

    expect(response.status).toBe(201);
    expect(response.headers.get('Location')).toBe('documents');
    expect(body).toEqual({ objectID: expect.stringMatching(/^[0-9a-f-]{36}$/), fileName: 'doc.pdf', mimeType: 'application/pdf' });
    expect(storage.entries.has(`documents/${body.objectID}`)).toBe(true);
  });

  it('routes the object to the bucket its MIME type maps to', async () => {
    expect((await upload(await png(), 'a.png', 'image/png')).headers.get('Location')).toBe('images');
    expect((await upload(Buffer.from('x'), 'a.zip', 'application/zip')).headers.get('Location')).toBe('archives');
    expect((await upload(Buffer.from('x'), 'a.bin', 'application/x-unknown')).headers.get('Location')).toBe('documents');
  });

  it('records the display name and MIME type as object metadata', async () => {
    const { objectID } = await (await upload(Buffer.from('x'), 'report.pdf', 'application/pdf')).json();

    expect(storage.entries.get(`documents/${objectID}`)?.metadata).toEqual({ bucket: 'documents', name: 'report.pdf', mimeType: 'application/pdf' });
  });

  it('generates a thumbnail alongside a raster image', async () => {
    const { objectID } = await (await upload(await png(), 'photo.png', 'image/png')).json();
    const thumbnail = storage.entries.get(`images/${objectID}-thumb`);

    expect(thumbnail).toBeDefined();
    expect(thumbnail?.contentType).toBe('image/jpeg');
    expect((await sharp(thumbnail!.content).metadata()).width).toBe(200);
  });

  it('generates no thumbnail for a non-image or an SVG', async () => {
    const { objectID: pdf } = await (await upload(Buffer.from('%PDF'), 'a.pdf', 'application/pdf')).json();
    const { objectID: svg } = await (await upload(Buffer.from('<svg/>'), 'a.svg', 'image/svg+xml')).json();

    expect(storage.entries.has(`documents/${pdf}-thumb`)).toBe(false);
    expect(storage.entries.has(`images/${svg}-thumb`)).toBe(false);
  });

  it('still stores the object when thumbnail generation fails', async () => {
    const response = await upload(Buffer.from('this is not a PNG'), 'broken.png', 'image/png');
    const { objectID } = await response.json();

    expect(response.status).toBe(201);
    expect(storage.entries.has(`images/${objectID}`)).toBe(true);
    expect(storage.entries.has(`images/${objectID}-thumb`)).toBe(false);
  });

  it('rejects a request without a file part', async () => {
    const form = new FormData();
    form.append('name', 'a.txt');
    form.append('mimeType', 'text/plain');

    const response = await fetch(`${app.base}/objects`, { method: 'POST', body: form });

    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ errorId: 'store.upload.file-missing', errorText: expect.any(String) });
  });

  it.each([
    ['name', 'mimeType'],
    ['mimeType', 'name'],
  ])('rejects a request without the %s field', async (_missing, present) => {
    const form = new FormData();
    form.append('file', new Blob([new Uint8Array(Buffer.from('x'))], { type: 'text/plain' }), 'a.txt');
    form.append(present, 'value');

    const response = await fetch(`${app.base}/objects`, { method: 'POST', body: form });

    expect(response.status).toBe(400);
    expect((await response.json()).errorId).toBe('store.upload.metadata-missing');
  });

  it('rejects an oversized upload as a bad request', async () => {
    vi.mocked(parseMultipart).mockRejectedValueOnce(new PayloadTooLargeError());

    const response = await upload(Buffer.from('x'), 'a.txt', 'text/plain');

    expect(response.status).toBe(400);
    expect((await response.json()).errorId).toBe('store.upload.file-too-large');
  });

  it('does not disguise an unexpected parse failure as a bad request', async () => {
    vi.mocked(parseMultipart).mockRejectedValueOnce(new Error('unexpected'));

    const response = await upload(Buffer.from('x'), 'a.txt', 'text/plain');

    expect(response.status).toBe(500);
    expect((await response.json()).errorId).toBe('store.internal-error');
  });
});

describe('getObjectByID', () => {
  it('streams the content back with the object headers', async () => {
    const { objectID } = await (await upload(Buffer.from('file body'), 'note.txt', 'text/plain')).json();

    const response = await fetch(`${app.base}/objects/documents/${objectID}`);

    expect(response.status).toBe(200);
    expect(response.headers.get('X-Object-Name')).toBe(objectID);
    expect(response.headers.get('X-Object-Bucket')).toBe('documents');
    expect(response.headers.get('Content-Type')).toBe('text/plain');
    expect(await response.text()).toBe('file body');
  });

  it('answers 404 for an object that does not exist', async () => {
    const response = await fetch(`${app.base}/objects/documents/absent`);

    expect(response.status).toBe(404);
    expect(await response.json()).toEqual({ errorId: 'store.object.not-found', errorText: expect.any(String) });
  });
});

describe('getObjectUriByID', () => {
  it('answers with a URI for a stored object', async () => {
    const { objectID } = await (await upload(Buffer.from('x'), 'a.txt', 'text/plain')).json();

    const response = await fetch(`${app.base}/objects/documents/${objectID}/uri`);

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ uri: `https://signed.example/documents/${objectID}` });
  });

  it('answers 404 for an object that does not exist', async () => {
    expect((await fetch(`${app.base}/objects/documents/absent/uri`)).status).toBe(404);
  });
});

describe('getThumbnailUriByID', () => {
  it('answers with a URI for a generated thumbnail', async () => {
    const { objectID } = await (await upload(await png(), 'photo.png', 'image/png')).json();

    const response = await fetch(`${app.base}/objects/images/${objectID}/thumbnail-uri`);

    expect(await response.json()).toEqual({ uri: `https://signed.example/images/${objectID}-thumb` });
  });

  it('answers 404 when the object has no thumbnail', async () => {
    const { objectID } = await (await upload(Buffer.from('%PDF'), 'a.pdf', 'application/pdf')).json();

    const response = await fetch(`${app.base}/objects/documents/${objectID}/thumbnail-uri`);

    expect(response.status).toBe(404);
    expect((await response.json()).errorId).toBe('store.thumbnail.not-found');
  });
});

describe('deleteObjectByID', () => {
  it('removes the object and its thumbnail', async () => {
    const { objectID } = await (await upload(await png(), 'photo.png', 'image/png')).json();

    const response = await fetch(`${app.base}/objects/images/${objectID}`, { method: 'DELETE' });

    expect(response.status).toBe(204);
    expect(storage.entries.has(`images/${objectID}`)).toBe(false);
    expect(storage.entries.has(`images/${objectID}-thumb`)).toBe(false);
  });

  it('answers 404 for an object that does not exist', async () => {
    const response = await fetch(`${app.base}/objects/documents/absent`, { method: 'DELETE' });

    expect(response.status).toBe(404);
    expect((await response.json()).errorId).toBe('store.object.not-found');
  });
});
