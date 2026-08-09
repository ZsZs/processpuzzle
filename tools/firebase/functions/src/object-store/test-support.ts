import type { Express } from 'express';
import type { AddressInfo } from 'node:net';
import { Readable } from 'node:stream';
import type { FirebaseFileStorageService, StoredObject } from './firebase-file-storage.service.js';

export interface RunningApp {
  readonly base: string;
  close(): Promise<void>;
}

/** Binds the app to an ephemeral port so specs can exercise it over real HTTP. */
export async function serve(app: Express): Promise<RunningApp> {
  const server = app.listen(0);
  await new Promise((resolve) => server.once('listening', resolve));
  const { port } = server.address() as AddressInfo;

  return {
    base: `http://127.0.0.1:${port}`,
    close: () => new Promise<void>((resolve, reject) => server.close((error) => (error ? reject(error) : resolve()))),
  };
}

export interface StoredEntry {
  content: Buffer;
  contentType: string;
  metadata: Record<string, string>;
}

export interface StorageStub {
  readonly entries: Map<string, StoredEntry>;
  readonly service: FirebaseFileStorageService;
  failWith(error: Error): void;
}

/**
 * In-memory stand-in for FirebaseFileStorageService. The handlers only ever see this
 * interface, so their behaviour can be pinned without Firebase, a network or an emulator.
 */
export function createStorageStub(): StorageStub {
  const entries = new Map<string, StoredEntry>();
  let failure: Error | undefined;

  const guard = () => {
    if (failure) throw failure;
  };

  const service = {
    async uploadObject(bucket: string, name: string, content: Buffer, contentType: string, metadata: Record<string, string>): Promise<void> {
      guard();
      entries.set(`${bucket}/${name}`, { content, contentType, metadata });
    },
    async getObject(bucket: string, name: string): Promise<StoredObject | undefined> {
      guard();
      const entry = entries.get(`${bucket}/${name}`);
      if (!entry) return undefined;
      return { stream: Readable.from([entry.content]), contentType: entry.contentType, metadata: entry.metadata };
    },
    async objectExists(bucket: string, name: string): Promise<boolean> {
      guard();
      return entries.has(`${bucket}/${name}`);
    },
    async getObjectUri(bucket: string, name: string): Promise<string> {
      guard();
      return `https://signed.example/${bucket}/${name}`;
    },
    async deleteObject(bucket: string, name: string): Promise<void> {
      guard();
      entries.delete(`${bucket}/${name}`);
    },
  };

  return {
    entries,
    service: service as unknown as FirebaseFileStorageService,
    failWith(error: Error) {
      failure = error;
    },
  };
}

export function uploadForm(content: Buffer, fileName: string, mimeType: string): FormData {
  const form = new FormData();
  form.append('file', new Blob([new Uint8Array(content)], { type: mimeType }), fileName);
  form.append('name', fileName);
  form.append('mimeType', mimeType);
  return form;
}
