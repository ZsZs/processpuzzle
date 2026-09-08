"use strict";
/** Mirrors the `minio.thumbnail` block of `minio-config.yaml` and MinIO's 1 hour presign expiry. */
var _a, _b;
Object.defineProperty(exports, "__esModule", { value: true });
exports.API_BASE_PATH = exports.MAX_UPLOAD_BYTES = exports.SIGNED_URI_TTL_MS = exports.THUMBNAIL_CONFIG = void 0;
exports.THUMBNAIL_CONFIG = {
    enabled: process.env.OBJECT_STORE_THUMBNAIL_ENABLED !== 'false',
    maxDimension: Number((_a = process.env.OBJECT_STORE_THUMBNAIL_MAX_DIMENSION) !== null && _a !== void 0 ? _a : 200),
    quality: Number((_b = process.env.OBJECT_STORE_THUMBNAIL_QUALITY) !== null && _b !== void 0 ? _b : 0.85),
};
exports.SIGNED_URI_TTL_MS = 60 * 60 * 1000;
/**
 * Cloud Functions caps a response at 32 MiB, so streaming a larger object back through
 * `getObjectByID` would fail anyway — reject it at upload instead of at download.
 */
exports.MAX_UPLOAD_BYTES = 32 * 1024 * 1024;
/** Path the Hosting rewrite delivers to this function; see `firebase.json`. */
exports.API_BASE_PATH = '/api/store';
//# sourceMappingURL=object-store.config.js.map