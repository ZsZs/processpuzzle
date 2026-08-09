import { logger } from 'firebase-functions';
import type { Request, Response } from 'express';
import { randomUUID } from 'node:crypto';
import { findBucketName, thumbnailKey } from './bucket-naming.js';
import { FirebaseFileStorageService } from './firebase-file-storage.service.js';
import { MultipartBody, parseMultipart, PayloadTooLargeError } from './multipart.js';
import { THUMBNAIL_CONFIG } from './object-store.config.js';
import { generateThumbnail, isThumbnailable } from './thumbnail-generator.js';

/**
 * The five operations of `processpuzzle-store-api.yaml`, backed by Firebase Storage
 * instead of MinIO. Status codes, headers and payloads are those of `ObjectEndpoint`
 * so that `ObjectStoreService` on the frontend needs no knowledge of which topology
 * it is talking to.
 */
export class ObjectStoreHandlers {
  constructor(private readonly storage: FirebaseFileStorageService = new FirebaseFileStorageService()) {}

  uploadObject = async (request: Request, response: Response): Promise<void> => {
    let body: MultipartBody;
    try {
      body = await parseMultipart(request);
    } catch (error) {
      if (error instanceof PayloadTooLargeError) {
        badRequest(response, 'store.upload.file-too-large', error.message);
        return;
      }
      throw error;
    }

    const file = body.files.find((candidate) => candidate.fieldName === 'file');
    const fileName = body.fields['name'];
    const mimeType = body.fields['mimeType'];
    if (!file) {
      badRequest(response, 'store.upload.file-missing', "Multipart part 'file' is required.");
      return;
    }
    if (!fileName || !mimeType) {
      badRequest(response, 'store.upload.metadata-missing', "Multipart fields 'name' and 'mimeType' are required.");
      return;
    }

    const bucketName = findBucketName(mimeType);
    const objectID = randomUUID();
    await this.storage.uploadObject(bucketName, objectID, file.content, mimeType, { bucket: bucketName, name: fileName, mimeType });

    if (THUMBNAIL_CONFIG.enabled && isThumbnailable(mimeType)) {
      await this.storeThumbnailBestEffort(bucketName, objectID, file.content);
    }

    response.setHeader('Location', bucketName);
    response.status(201).json({ objectID, fileName, mimeType });
  };

  getObjectByID = async (request: Request, response: Response): Promise<void> => {
    const { bucketName, objectID } = objectRef(request);
    const stored = await this.storage.getObject(bucketName, objectID);
    if (!stored) {
      objectNotFound(response, bucketName, objectID);
      return;
    }

    // `X-Object-Name` carries the object id, not the display name — that is what
    // MinioFileStorageService puts there, and the two implementations have to agree.
    response.setHeader('X-Object-Name', objectID);
    response.setHeader('X-Object-Bucket', bucketName);
    if (stored.contentType) response.setHeader('Content-Type', stored.contentType);

    stored.stream.on('error', (error) => {
      logger.error(`Failed to stream ${bucketName}/${objectID}`, error);
      response.destroy(error);
    });
    stored.stream.pipe(response);
  };

  deleteObjectByID = async (request: Request, response: Response): Promise<void> => {
    const { bucketName, objectID } = objectRef(request);
    if (!(await this.storage.objectExists(bucketName, objectID))) {
      objectNotFound(response, bucketName, objectID);
      return;
    }

    await this.storage.deleteObject(bucketName, objectID);
    // The thumbnail is an implementation detail of the upload, so it goes with the object
    // rather than being left behind as an unreachable orphan.
    await this.storage.deleteObject(bucketName, thumbnailKey(objectID));
    response.status(204).send();
  };

  getObjectUriByID = async (request: Request, response: Response): Promise<void> => {
    const { bucketName, objectID } = objectRef(request);
    if (!(await this.storage.objectExists(bucketName, objectID))) {
      objectNotFound(response, bucketName, objectID);
      return;
    }

    response.status(200).json({ uri: await this.storage.getObjectUri(bucketName, objectID) });
  };

  getThumbnailUriByID = async (request: Request, response: Response): Promise<void> => {
    const { bucketName, objectID } = objectRef(request);
    const thumbnail = thumbnailKey(objectID);
    if (!(await this.storage.objectExists(bucketName, thumbnail))) {
      notFound(response, 'store.thumbnail.not-found', `No thumbnail exists for object '${objectID}' in bucket '${bucketName}'.`);
      return;
    }

    response.status(200).json({ uri: await this.storage.getObjectUri(bucketName, thumbnail) });
  };

  /** Mirrors `UploadObject.storeThumbnailBestEffort`: a failed thumbnail never fails the upload. */
  private async storeThumbnailBestEffort(bucketName: string, objectID: string, source: Buffer): Promise<void> {
    const thumbnail = thumbnailKey(objectID);
    try {
      const content = await generateThumbnail(source, THUMBNAIL_CONFIG.maxDimension, THUMBNAIL_CONFIG.quality);
      await this.storage.uploadObject(bucketName, thumbnail, content, 'image/jpeg', { bucket: bucketName, name: thumbnail, mimeType: 'image/jpeg' });
    } catch (error) {
      logger.warn(`Failed to generate/store thumbnail for ${bucketName}/${objectID}`, error);
    }
  }
}

function objectRef(request: Request): { bucketName: string; objectID: string } {
  const { bucketName, objectID } = request.params as { bucketName: string; objectID: string };
  return { bucketName, objectID };
}

function badRequest(response: Response, errorId: string, errorText: string): void {
  response.status(400).json({ errorId, errorText });
}

function notFound(response: Response, errorId: string, errorText: string): void {
  response.status(404).json({ errorId, errorText });
}

function objectNotFound(response: Response, bucketName: string, objectID: string): void {
  notFound(response, 'store.object.not-found', `Object '${objectID}' does not exist in bucket '${bucketName}'.`);
}
