import { getApps, initializeApp } from 'firebase-admin/app';
import { getStorage } from 'firebase-admin/storage';
import type { Bucket, File } from '@google-cloud/storage';
import { randomUUID } from 'node:crypto';
import type { Readable } from 'node:stream';
import { objectPath } from './bucket-naming.js';
import { SIGNED_URI_TTL_MS } from './object-store.config.js';

export interface StoredObject {
  readonly stream: Readable;
  readonly contentType?: string;
  readonly metadata: Readonly<Record<string, string>>;
}

/**
 * Firebase pendant of `MinioFileStorageService`. Bucket lifecycle operations have no
 * counterpart — the logical bucket is only a key prefix (see `bucket-naming.ts`), so
 * there is nothing to create, drop or probe for existence.
 */
export class FirebaseFileStorageService {
  private static readonly DOWNLOAD_TOKEN_KEY = 'firebaseStorageDownloadTokens';

  async uploadObject(bucketName: string, objectName: string, content: Buffer, contentType: string, metadata: Record<string, string>): Promise<void> {
    await this.file(bucketName, objectName).save(content, {
      contentType,
      resumable: false,
      metadata: {
        contentType,
        // A download token is what makes the emulator fallback in `getObjectUri` work, and
        // it costs nothing in production where signed URLs are used instead.
        metadata: { ...metadata, [FirebaseFileStorageService.DOWNLOAD_TOKEN_KEY]: randomUUID() },
      },
    });
  }

  /** Undefined — rather than a throw — when the object does not exist, so the caller can answer 404. */
  async getObject(bucketName: string, objectName: string): Promise<StoredObject | undefined> {
    const file = this.file(bucketName, objectName);
    const metadata = await this.metadataOf(file);
    if (!metadata) return undefined;

    return {
      stream: file.createReadStream(),
      contentType: typeof metadata.contentType === 'string' ? metadata.contentType : undefined,
      metadata: (metadata.metadata ?? {}) as Record<string, string>,
    };
  }

  async objectExists(bucketName: string, objectName: string): Promise<boolean> {
    const [exists] = await this.file(bucketName, objectName).exists();
    return exists;
  }

  /**
   * Short-lived read URI. Production signs with the function's service account, which needs
   * `roles/iam.serviceAccountTokenCreator` to call signBlob. The Storage emulator implements
   * no signing at all, so there we hand back the emulator's own tokenized media URL — same
   * contract from the caller's point of view, no expiry.
   */
  async getObjectUri(bucketName: string, objectName: string): Promise<string> {
    const file = this.file(bucketName, objectName);
    const emulatorHost = FirebaseFileStorageService.emulatorHost();
    if (emulatorHost) return this.emulatorDownloadUri(file, emulatorHost);

    const [uri] = await file.getSignedUrl({ action: 'read', expires: Date.now() + SIGNED_URI_TTL_MS });
    return uri;
  }

  async deleteObject(bucketName: string, objectName: string): Promise<void> {
    await this.file(bucketName, objectName).delete({ ignoreNotFound: true });
  }

  private file(bucketName: string, objectName: string): File {
    return FirebaseFileStorageService.bucket().file(objectPath(bucketName, objectName));
  }

  private async metadataOf(file: File): Promise<Record<string, unknown> | undefined> {
    try {
      const [metadata] = await file.getMetadata();
      return metadata as Record<string, unknown>;
    } catch (error) {
      if (FirebaseFileStorageService.isNotFound(error)) return undefined;
      throw error;
    }
  }

  private async emulatorDownloadUri(file: File, emulatorHost: string): Promise<string> {
    const metadata = await this.metadataOf(file);
    const custom = (metadata?.metadata ?? {}) as Record<string, string>;
    const token = custom[FirebaseFileStorageService.DOWNLOAD_TOKEN_KEY]?.split(',')[0];
    const path = `${emulatorHost}/v0/b/${file.bucket.name}/o/${encodeURIComponent(file.name)}?alt=media`;
    return token ? `${path}&token=${token}` : path;
  }

  private static bucket(): Bucket {
    if (!getApps().length) initializeApp();
    const bucketName = process.env.OBJECT_STORE_BUCKET;
    return bucketName ? getStorage().bucket(bucketName) : getStorage().bucket();
  }

  /** `STORAGE_EMULATOR_HOST` is set by the emulator suite, with or without a scheme. */
  private static emulatorHost(): string | undefined {
    const host = process.env.STORAGE_EMULATOR_HOST ?? process.env.FIREBASE_STORAGE_EMULATOR_HOST;
    if (!host) return undefined;
    return host.startsWith('http://') || host.startsWith('https://') ? host : `http://${host}`;
  }

  private static isNotFound(error: unknown): boolean {
    return typeof error === 'object' && error !== null && (error as { code?: unknown }).code === 404;
  }
}
