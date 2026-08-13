/** Mirrors the `minio.thumbnail` block of `minio-config.yaml` and MinIO's 1 hour presign expiry. */

export interface ThumbnailConfig {
  readonly enabled: boolean;
  readonly maxDimension: number;
  readonly quality: number;
}

export const THUMBNAIL_CONFIG: ThumbnailConfig = {
  enabled: process.env.OBJECT_STORE_THUMBNAIL_ENABLED !== 'false',
  maxDimension: Number(process.env.OBJECT_STORE_THUMBNAIL_MAX_DIMENSION ?? 200),
  quality: Number(process.env.OBJECT_STORE_THUMBNAIL_QUALITY ?? 0.85),
};

export const SIGNED_URI_TTL_MS = 60 * 60 * 1000;

/**
 * Cloud Functions caps a response at 32 MiB, so streaming a larger object back through
 * `getObjectByID` would fail anyway — reject it at upload instead of at download.
 */
export const MAX_UPLOAD_BYTES = 32 * 1024 * 1024;

/** Path the Hosting rewrite delivers to this function; see `firebase.json`. */
export const API_BASE_PATH = '/api/store';
