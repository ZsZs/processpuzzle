"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ObjectStoreHandlers = void 0;
const firebase_functions_1 = require("firebase-functions");
const node_crypto_1 = require("node:crypto");
const bucket_naming_js_1 = require("./bucket-naming.js");
const firebase_file_storage_service_js_1 = require("./firebase-file-storage.service.js");
const multipart_js_1 = require("./multipart.js");
const object_store_config_js_1 = require("./object-store.config.js");
const thumbnail_generator_js_1 = require("./thumbnail-generator.js");
/**
 * The five operations of `processpuzzle-store-api.yaml`, backed by Firebase Storage
 * instead of MinIO. Status codes, headers and payloads are those of `ObjectEndpoint`
 * so that `ObjectStoreService` on the frontend needs no knowledge of which topology
 * it is talking to.
 */
class ObjectStoreHandlers {
    constructor(storage = new firebase_file_storage_service_js_1.FirebaseFileStorageService()) {
        this.storage = storage;
        this.uploadObject = async (request, response) => {
            let body;
            try {
                body = await (0, multipart_js_1.parseMultipart)(request);
            }
            catch (error) {
                if (error instanceof multipart_js_1.PayloadTooLargeError) {
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
            const bucketName = (0, bucket_naming_js_1.findBucketName)(mimeType);
            const objectID = (0, node_crypto_1.randomUUID)();
            await this.storage.uploadObject(bucketName, objectID, file.content, mimeType, { bucket: bucketName, name: fileName, mimeType });
            if (object_store_config_js_1.THUMBNAIL_CONFIG.enabled && (0, thumbnail_generator_js_1.isThumbnailable)(mimeType)) {
                await this.storeThumbnailBestEffort(bucketName, objectID, file.content);
            }
            response.setHeader('Location', bucketName);
            response.status(201).json({ objectID, fileName, mimeType });
        };
        this.getObjectByID = async (request, response) => {
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
            if (stored.contentType)
                response.setHeader('Content-Type', stored.contentType);
            stored.stream.on('error', (error) => {
                firebase_functions_1.logger.error(`Failed to stream ${bucketName}/${objectID}`, error);
                response.destroy(error);
            });
            stored.stream.pipe(response);
        };
        this.deleteObjectByID = async (request, response) => {
            const { bucketName, objectID } = objectRef(request);
            if (!(await this.storage.objectExists(bucketName, objectID))) {
                objectNotFound(response, bucketName, objectID);
                return;
            }
            await this.storage.deleteObject(bucketName, objectID);
            // The thumbnail is an implementation detail of the upload, so it goes with the object
            // rather than being left behind as an unreachable orphan.
            await this.storage.deleteObject(bucketName, (0, bucket_naming_js_1.thumbnailKey)(objectID));
            response.status(204).send();
        };
        this.getObjectUriByID = async (request, response) => {
            const { bucketName, objectID } = objectRef(request);
            if (!(await this.storage.objectExists(bucketName, objectID))) {
                objectNotFound(response, bucketName, objectID);
                return;
            }
            response.status(200).json({ uri: await this.storage.getObjectUri(bucketName, objectID) });
        };
        this.getThumbnailUriByID = async (request, response) => {
            const { bucketName, objectID } = objectRef(request);
            const thumbnail = (0, bucket_naming_js_1.thumbnailKey)(objectID);
            if (!(await this.storage.objectExists(bucketName, thumbnail))) {
                notFound(response, 'store.thumbnail.not-found', `No thumbnail exists for object '${objectID}' in bucket '${bucketName}'.`);
                return;
            }
            response.status(200).json({ uri: await this.storage.getObjectUri(bucketName, thumbnail) });
        };
    }
    /** Mirrors `UploadObject.storeThumbnailBestEffort`: a failed thumbnail never fails the upload. */
    async storeThumbnailBestEffort(bucketName, objectID, source) {
        const thumbnail = (0, bucket_naming_js_1.thumbnailKey)(objectID);
        try {
            const content = await (0, thumbnail_generator_js_1.generateThumbnail)(source, object_store_config_js_1.THUMBNAIL_CONFIG.maxDimension, object_store_config_js_1.THUMBNAIL_CONFIG.quality);
            await this.storage.uploadObject(bucketName, thumbnail, content, 'image/jpeg', { bucket: bucketName, name: thumbnail, mimeType: 'image/jpeg' });
        }
        catch (error) {
            firebase_functions_1.logger.warn(`Failed to generate/store thumbnail for ${bucketName}/${objectID}`, error);
        }
    }
}
exports.ObjectStoreHandlers = ObjectStoreHandlers;
function objectRef(request) {
    const { bucketName, objectID } = request.params;
    return { bucketName, objectID };
}
function badRequest(response, errorId, errorText) {
    response.status(400).json({ errorId, errorText });
}
function notFound(response, errorId, errorText) {
    response.status(404).json({ errorId, errorText });
}
function objectNotFound(response, bucketName, objectID) {
    notFound(response, 'store.object.not-found', `Object '${objectID}' does not exist in bucket '${bucketName}'.`);
}
//# sourceMappingURL=object-store.handlers.js.map