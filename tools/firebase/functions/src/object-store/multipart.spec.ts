import { describe, expect, it, vi } from 'vitest';
import type { Request } from 'express';
import { Readable } from 'node:stream';

// A small limit keeps the oversize case fast; multipart.ts reads it at module load.
vi.mock('./object-store.config.js', () => ({ MAX_UPLOAD_BYTES: 64 }));

const { parseMultipart, PayloadTooLargeError } = await import('./multipart.js');

interface SerializedForm {
  headers: Record<string, string>;
  rawBody: Buffer;
}

async function serialize(form: FormData): Promise<SerializedForm> {
  const response = new Response(form);
  return {
    headers: { 'content-type': response.headers.get('content-type') ?? '' },
    rawBody: Buffer.from(await response.arrayBuffer()),
  };
}

/** What Cloud Functions hands the handler: body already drained into rawBody. */
function bufferedRequest({ headers, rawBody }: SerializedForm): Request {
  return { headers, rawBody } as unknown as Request;
}

/** What a directly-served express app hands the handler: a readable stream. */
function streamedRequest({ headers, rawBody }: SerializedForm): Request {
  const request = Readable.from([rawBody]) as Readable & { headers: Record<string, string> };
  request.headers = headers;
  return request as unknown as Request;
}

function formWith(content: Buffer, fileName = 'a.txt', mimeType = 'text/plain'): FormData {
  const form = new FormData();
  form.append('file', new Blob([new Uint8Array(content)], { type: mimeType }), fileName);
  form.append('name', fileName);
  form.append('mimeType', mimeType);
  return form;
}

describe('parseMultipart', () => {
  it('reads fields and the file from rawBody', async () => {
    const body = await parseMultipart(bufferedRequest(await serialize(formWith(Buffer.from('hello')))));

    expect(body.fields).toEqual({ name: 'a.txt', mimeType: 'text/plain' });
    expect(body.files).toHaveLength(1);
    expect(body.files[0].fieldName).toBe('file');
    expect(body.files[0].fileName).toBe('a.txt');
    expect(body.files[0].mimeType).toBe('text/plain');
    expect(body.files[0].content.toString()).toBe('hello');
  });

  it('reads the same payload from a streamed request', async () => {
    const body = await parseMultipart(streamedRequest(await serialize(formWith(Buffer.from('hello')))));

    expect(body.fields['name']).toBe('a.txt');
    expect(body.files[0].content.toString()).toBe('hello');
  });

  it('returns no files when the form carries only fields', async () => {
    const form = new FormData();
    form.append('name', 'a.txt');
    form.append('mimeType', 'text/plain');

    const body = await parseMultipart(bufferedRequest(await serialize(form)));

    expect(body.files).toHaveLength(0);
    expect(body.fields).toEqual({ name: 'a.txt', mimeType: 'text/plain' });
  });

  it('rejects a file larger than the limit', async () => {
    const oversize = Buffer.alloc(128, 0x61);

    await expect(parseMultipart(bufferedRequest(await serialize(formWith(oversize))))).rejects.toBeInstanceOf(PayloadTooLargeError);
  });

  // busboy trips its own limit once the file reaches MAX_UPLOAD_BYTES, not once it passes
  // it, so the largest accepted payload is one byte under. Pinned rather than assumed.
  it('keeps a file just under the limit', async () => {
    const body = await parseMultipart(bufferedRequest(await serialize(formWith(Buffer.alloc(63, 0x61)))));

    expect(body.files[0].content).toHaveLength(63);
  });

  it('rejects a file of exactly the limit', async () => {
    await expect(parseMultipart(bufferedRequest(await serialize(formWith(Buffer.alloc(64, 0x61)))))).rejects.toBeInstanceOf(PayloadTooLargeError);
  });

  // Not the same path as an unparseable body: the content type is valid, so busboy is
  // constructed and reports the failure asynchronously through its `error` event.
  it('rejects a body that ends in the middle of the form', async () => {
    const { headers, rawBody } = await serialize(formWith(Buffer.from('hello')));

    await expect(parseMultipart(bufferedRequest({ headers, rawBody: rawBody.subarray(0, rawBody.length - 20) }))).rejects.toThrow();
  });

  it('rejects a body that is not parseable as multipart', async () => {
    const request = { headers: { 'content-type': 'text/plain' }, rawBody: Buffer.from('nonsense') } as unknown as Request;

    await expect(parseMultipart(request)).rejects.toThrow();
  });
});
