"use strict";
/**
 * Firebase pendant of `BucketNameFinder` and `ThumbnailNaming` in `processpuzzle-store`.
 *
 * A Firebase project has a single default Storage bucket, so MinIO's logical buckets
 * (`images`, `documents`, ...) become key prefixes inside it: `images/<objectID>`.
 * `bucketName` therefore keeps its meaning on the wire and in `ArtifactAttr.bucket`,
 * and artifact references stay portable between the Docker and Firebase topologies.
 *
 * MIME_TYPE_BUCKETS mirrors `minio-config.yaml`; the parity test asserts they match.
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.THUMBNAIL_SUFFIX = exports.MIME_TYPE_BUCKETS = exports.LOGICAL_BUCKETS = exports.DEFAULT_BUCKET = void 0;
exports.findBucketName = findBucketName;
exports.thumbnailKey = thumbnailKey;
exports.objectPath = objectPath;
exports.DEFAULT_BUCKET = 'documents';
exports.LOGICAL_BUCKETS = ['configuration', 'text', 'images', 'documents', 'audio', 'video', 'archives', 'logs'];
exports.MIME_TYPE_BUCKETS = {
    'application/json': 'logs',
    'application/xml': 'configuration',
    'application/yaml': 'configuration',
    'application/x-yaml': 'configuration',
    'audio/mpeg': 'audio',
    'audio/wav': 'audio',
    'audio/ogg': 'audio',
    'image/png': 'images',
    'image/jpeg': 'images',
    'image/gif': 'images',
    'image/svg+xml': 'images',
    'image/webp': 'images',
    'image/x-icon': 'images',
    'application/javascript': 'text',
    'application/msword': 'documents',
    'application/pdf': 'documents',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'documents',
    'application/vnd.ms-excel': 'documents',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'documents',
    'application/vnd.ms-powerpoint': 'documents',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'documents',
    'application/x-tar': 'archives',
    'application/x-rar-compressed': 'archives',
    'application/x-7z-compressed': 'archives',
    'application/x-log': 'logs',
    'application/zip': 'archives',
    'video/mp4': 'video',
    'video/mpeg': 'video',
    'video/quicktime': 'video',
    'text/css': 'text',
    'text/plain': 'documents',
    'text/markdown': 'text',
    'text/html': 'text',
    'text/x-log': 'logs',
};
exports.THUMBNAIL_SUFFIX = '-thumb';
/**
 * Spring's relaxed binding drops every non-alphanumeric character from a map key, which
 * is why `BucketNameFinder` normalizes its argument the same way before the lookup. The
 * normalization is reproduced here — including its case sensitivity — so that a given
 * MIME type lands in the same bucket on both platforms.
 */
function normalize(mimeType) {
    return mimeType.replace(/[^a-zA-Z0-9]/g, '');
}
const NORMALIZED_MIME_TYPE_BUCKETS = new Map(Object.entries(exports.MIME_TYPE_BUCKETS).map(([mimeType, bucket]) => [normalize(mimeType), bucket]));
function findBucketName(mimeType) {
    var _a;
    if (!mimeType)
        return exports.DEFAULT_BUCKET;
    return (_a = NORMALIZED_MIME_TYPE_BUCKETS.get(normalize(mimeType))) !== null && _a !== void 0 ? _a : exports.DEFAULT_BUCKET;
}
function thumbnailKey(objectID) {
    return objectID + exports.THUMBNAIL_SUFFIX;
}
/** Logical bucket + object name as one key in the project's default Storage bucket. */
function objectPath(bucketName, objectName) {
    return `${bucketName}/${objectName}`;
}
//# sourceMappingURL=bucket-naming.js.map