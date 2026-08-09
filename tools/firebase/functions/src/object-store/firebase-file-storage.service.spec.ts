import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Readable } from 'node:stream';

const { getApps, initializeApp, getStorage } = vi.hoisted(() => ({
  getApps: vi.fn<() => unknown[]>(),
  initializeApp: vi.fn(),
  getStorage: vi.fn(),
}));

vi.mock('firebase-admin/app', () => ({ getApps, initializeApp }));
vi.mock('firebase-admin/storage', () => ({ getStorage }));

const { FirebaseFileStorageService } = await import('./firebase-file-storage.service.js');

const NOT_FOUND = Object.assign(new Error('No such object'), { code: 404 });

function createFile() {
  return {
    name: '',
    bucket: { name: 'test-bucket.appspot.com' },
    save: vi.fn().mockResolvedValue(undefined),
    exists: vi.fn().mockResolvedValue([true]),
    getMetadata: vi.fn().mockResolvedValue([{ contentType: 'text/plain', metadata: { name: 'note.txt', bucket: 'documents' } }]),
    createReadStream: vi.fn(() => Readable.from([Buffer.from('content')])),
    delete: vi.fn().mockResolvedValue(undefined),
    getSignedUrl: vi.fn().mockResolvedValue(['https://signed.example/object']),
  };
}

let file: ReturnType<typeof createFile>;
let bucket: { name: string; file: ReturnType<typeof vi.fn> };
let storage: InstanceType<typeof FirebaseFileStorageService>;
const savedEnv = { ...process.env };

beforeEach(() => {
  vi.clearAllMocks();
  file = createFile();
  bucket = { name: 'test-bucket.appspot.com', file: vi.fn((path: string) => ((file.name = path), file)) };
  getApps.mockReturnValue([{}]);
  getStorage.mockReturnValue({ bucket: vi.fn(() => bucket) });
  delete process.env.STORAGE_EMULATOR_HOST;
  delete process.env.FIREBASE_STORAGE_EMULATOR_HOST;
  delete process.env.OBJECT_STORE_BUCKET;
  delete process.env.GCLOUD_PROJECT;
  delete process.env.GOOGLE_CLOUD_PROJECT;
  delete process.env.FIREBASE_CONFIG;
  storage = new FirebaseFileStorageService();
});

afterEach(() => {
  process.env = { ...savedEnv };
});

describe('bucket resolution', () => {
  it('initializes the admin app only when none exists', async () => {
    getApps.mockReturnValue([]);
    await storage.objectExists('documents', 'obj-1');
    expect(initializeApp).toHaveBeenCalledOnce();

    getApps.mockReturnValue([{}]);
    await storage.objectExists('documents', 'obj-1');
    expect(initializeApp).toHaveBeenCalledOnce();
  });

  it('names the project default bucket, rather than letting FIREBASE_CONFIG offer the legacy one', async () => {
    const bucketOf = vi.fn(() => bucket);
    getStorage.mockReturnValue({ bucket: bucketOf });
    process.env.GCLOUD_PROJECT = 'a-project';

    await storage.objectExists('documents', 'obj-1');

    expect(bucketOf).toHaveBeenCalledWith('a-project.firebasestorage.app');
  });

  it('falls back to GOOGLE_CLOUD_PROJECT, then to FIREBASE_CONFIG, for the project id', async () => {
    const bucketOf = vi.fn(() => bucket);
    getStorage.mockReturnValue({ bucket: bucketOf });

    process.env.GOOGLE_CLOUD_PROJECT = 'from-gcp-env';
    await storage.objectExists('documents', 'obj-1');
    expect(bucketOf).toHaveBeenLastCalledWith('from-gcp-env.firebasestorage.app');

    delete process.env.GOOGLE_CLOUD_PROJECT;
    process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: 'from-config' });
    await storage.objectExists('documents', 'obj-1');
    expect(bucketOf).toHaveBeenLastCalledWith('from-config.firebasestorage.app');
  });

  it('leaves the name to the admin SDK when no project id is discoverable', async () => {
    const bucketOf = vi.fn(() => bucket);
    getStorage.mockReturnValue({ bucket: bucketOf });
    process.env.FIREBASE_CONFIG = 'not json';

    await storage.objectExists('documents', 'obj-1');

    expect(bucketOf).toHaveBeenCalledWith();
  });

  it('honours the OBJECT_STORE_BUCKET override', async () => {
    const bucketOf = vi.fn(() => bucket);
    getStorage.mockReturnValue({ bucket: bucketOf });
    process.env.GCLOUD_PROJECT = 'a-project';
    process.env.OBJECT_STORE_BUCKET = 'explicit-bucket';

    await storage.objectExists('documents', 'obj-1');

    expect(bucketOf).toHaveBeenCalledWith('explicit-bucket');
  });

  it('addresses the object by logical bucket prefix', async () => {
    await storage.objectExists('images', 'obj-1');

    expect(bucket.file).toHaveBeenCalledWith('images/obj-1');
  });
});

describe('uploadObject', () => {
  it('stores content, contentType and custom metadata without resumable upload', async () => {
    const content = Buffer.from('payload');

    await storage.uploadObject('documents', 'obj-1', content, 'text/plain', { bucket: 'documents', name: 'note.txt', mimeType: 'text/plain' });

    expect(file.save).toHaveBeenCalledOnce();
    const [saved, options] = file.save.mock.calls[0];
    expect(saved).toBe(content);
    expect(options.contentType).toBe('text/plain');
    expect(options.resumable).toBe(false);
    expect(options.metadata.contentType).toBe('text/plain');
    expect(options.metadata.metadata).toMatchObject({ bucket: 'documents', name: 'note.txt', mimeType: 'text/plain' });
  });

  it('attaches a fresh download token to every object', async () => {
    await storage.uploadObject('documents', 'obj-1', Buffer.from('a'), 'text/plain', {});
    await storage.uploadObject('documents', 'obj-2', Buffer.from('b'), 'text/plain', {});

    const tokenOf = (call: number) => file.save.mock.calls[call][1].metadata.metadata['firebaseStorageDownloadTokens'];
    expect(tokenOf(0)).toMatch(/^[0-9a-f-]{36}$/);
    expect(tokenOf(1)).not.toBe(tokenOf(0));
  });
});

describe('getObject', () => {
  it('returns the stream, content type and custom metadata', async () => {
    const stored = await storage.getObject('documents', 'obj-1');

    expect(stored?.contentType).toBe('text/plain');
    expect(stored?.metadata).toEqual({ name: 'note.txt', bucket: 'documents' });
    expect(file.createReadStream).toHaveBeenCalledOnce();
  });

  it('returns undefined when the object does not exist', async () => {
    file.getMetadata.mockRejectedValue(NOT_FOUND);

    expect(await storage.getObject('documents', 'absent')).toBeUndefined();
    expect(file.createReadStream).not.toHaveBeenCalled();
  });

  it('propagates errors that are not a missing object', async () => {
    file.getMetadata.mockRejectedValue(Object.assign(new Error('boom'), { code: 500 }));

    await expect(storage.getObject('documents', 'obj-1')).rejects.toThrow('boom');
  });

  it('copes with an object that carries no custom metadata', async () => {
    file.getMetadata.mockResolvedValue([{}]);

    const stored = await storage.getObject('documents', 'obj-1');

    expect(stored?.contentType).toBeUndefined();
    expect(stored?.metadata).toEqual({});
  });
});

describe('objectExists', () => {
  it('reports what the storage client reports', async () => {
    file.exists.mockResolvedValue([false]);
    expect(await storage.objectExists('documents', 'obj-1')).toBe(false);

    file.exists.mockResolvedValue([true]);
    expect(await storage.objectExists('documents', 'obj-1')).toBe(true);
  });
});

describe('getObjectUri', () => {
  it('signs a short-lived URL in production', async () => {
    const before = Date.now();

    const uri = await storage.getObjectUri('documents', 'obj-1');

    expect(uri).toBe('https://signed.example/object');
    const [options] = file.getSignedUrl.mock.calls[0];
    expect(options.action).toBe('read');
    expect(options.expires).toBeGreaterThanOrEqual(before + 60 * 60 * 1000);
    expect(options.expires).toBeLessThanOrEqual(Date.now() + 60 * 60 * 1000);
  });

  it('falls back to the emulator media URL, which cannot sign', async () => {
    process.env.STORAGE_EMULATOR_HOST = 'http://127.0.0.1:9199';
    file.getMetadata.mockResolvedValue([{ metadata: { firebaseStorageDownloadTokens: 'token-1' } }]);

    const uri = await storage.getObjectUri('images', 'obj-1');

    expect(uri).toBe('http://127.0.0.1:9199/v0/b/test-bucket.appspot.com/o/images%2Fobj-1?alt=media&token=token-1');
    expect(file.getSignedUrl).not.toHaveBeenCalled();
  });

  it('adds the scheme when the emulator host omits it', async () => {
    process.env.STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
    file.getMetadata.mockResolvedValue([{ metadata: { firebaseStorageDownloadTokens: 'token-1' } }]);

    expect(await storage.getObjectUri('images', 'obj-1')).toMatch(/^http:\/\/127\.0\.0\.1:9199\//);
  });

  it('accepts the FIREBASE_STORAGE_EMULATOR_HOST spelling', async () => {
    process.env.FIREBASE_STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
    file.getMetadata.mockResolvedValue([{ metadata: { firebaseStorageDownloadTokens: 'token-1' } }]);

    expect(await storage.getObjectUri('images', 'obj-1')).toContain('/v0/b/');
  });

  it('uses only the first of several download tokens', async () => {
    process.env.STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
    file.getMetadata.mockResolvedValue([{ metadata: { firebaseStorageDownloadTokens: 'first,second' } }]);

    expect(await storage.getObjectUri('images', 'obj-1')).toContain('&token=first');
  });

  it('omits the token when the object has none', async () => {
    process.env.STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
    file.getMetadata.mockResolvedValue([{ metadata: {} }]);

    const uri = await storage.getObjectUri('images', 'obj-1');

    expect(uri).toContain('?alt=media');
    expect(uri).not.toContain('token=');
  });
});

describe('deleteObject', () => {
  it('ignores an already absent object, so deletes stay idempotent', async () => {
    await storage.deleteObject('documents', 'obj-1');

    expect(file.delete).toHaveBeenCalledWith({ ignoreNotFound: true });
  });
});
