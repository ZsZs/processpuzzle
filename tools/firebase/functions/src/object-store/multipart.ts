import Busboy from 'busboy';
import type { Request } from 'express';
import { MAX_UPLOAD_BYTES } from './object-store.config.js';

export interface UploadedFile {
  readonly fieldName: string;
  readonly fileName: string;
  readonly mimeType: string;
  readonly content: Buffer;
}

export interface MultipartBody {
  readonly fields: Readonly<Record<string, string>>;
  readonly files: readonly UploadedFile[];
}

export class PayloadTooLargeError extends Error {
  constructor() {
    super(`Uploaded file exceeds the ${MAX_UPLOAD_BYTES} byte limit.`);
    this.name = 'PayloadTooLargeError';
  }
}

/**
 * Cloud Functions reads the whole request into `rawBody` before the handler runs, so the
 * request stream is already consumed and `multer` and friends never see a chunk. Feeding
 * `rawBody` into busboy is the supported way to read multipart in a function; the `pipe`
 * branch keeps the same code working when the express app is served directly (tests, a
 * local `express().listen()`).
 */
export function parseMultipart(request: Request): Promise<MultipartBody> {
  return new Promise((resolve, reject) => {
    const busboy = Busboy({ headers: request.headers, limits: { fileSize: MAX_UPLOAD_BYTES, files: 1 } });
    const fields: Record<string, string> = {};
    const files: UploadedFile[] = [];
    let settled = false;
    let truncated = false;

    const fail = (error: Error) => {
      if (settled) return;
      settled = true;
      reject(error);
    };

    const succeed = () => {
      if (settled) return;
      settled = true;
      if (truncated) reject(new PayloadTooLargeError());
      else resolve({ fields, files });
    };

    busboy.on('field', (name, value) => {
      fields[name] = value;
    });

    busboy.on('file', (fieldName, stream, info) => {
      const chunks: Buffer[] = [];
      stream.on('data', (chunk: Buffer) => chunks.push(chunk));
      stream.on('limit', () => {
        truncated = true;
      });
      stream.on('error', fail);
      stream.on('end', () => {
        files.push({ fieldName, fileName: info.filename, mimeType: info.mimeType, content: Buffer.concat(chunks) });
      });
    });

    busboy.on('error', (error: unknown) => fail(error instanceof Error ? error : new Error(String(error))));
    // busboy 1.x signals completion with `close`; `finish` is the 0.x name. Listening to
    // both keeps the parser working across either resolution, the guard makes it idempotent.
    busboy.on('close', succeed);
    busboy.on('finish', succeed);

    const rawBody = (request as Request & { rawBody?: Buffer }).rawBody;
    if (rawBody) busboy.end(rawBody);
    else request.pipe(busboy);
  });
}
