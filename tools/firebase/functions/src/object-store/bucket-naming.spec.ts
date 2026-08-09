import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';
import { DEFAULT_BUCKET, findBucketName, LOGICAL_BUCKETS, MIME_TYPE_BUCKETS, objectPath, THUMBNAIL_SUFFIX, thumbnailKey } from './bucket-naming.js';
import { THUMBNAIL_CONFIG } from './object-store.config.js';

interface MinioConfig {
  minio: {
    buckets: Record<string, string>;
    'mime-types': Record<string, string>;
    thumbnail: { enabled: boolean; 'max-dimension': number; quality: number };
  };
}

const MINIO_CONFIG_PATH = join(__dirname, '../../../../../libs/java-shared/processpuzzle-store/src/main/resources/minio-config.yaml');

function readMinioConfig(): MinioConfig['minio'] {
  return (parse(readFileSync(MINIO_CONFIG_PATH, 'utf8')) as MinioConfig).minio;
}

/**
 * The Firebase and MinIO implementations serve the same API, so a given MIME type has to
 * land in the same bucket on both. Nothing generates one table from the other — this test
 * is what keeps them from drifting apart.
 */
describe('parity with minio-config.yaml', () => {
  const minio = readMinioConfig();

  it('routes every MIME type to the same bucket as processpuzzle-store', () => {
    expect(MIME_TYPE_BUCKETS).toEqual(minio['mime-types']);
  });

  it('knows the same logical buckets', () => {
    expect([...LOGICAL_BUCKETS].sort()).toEqual(Object.values(minio.buckets).sort());
  });

  it('falls back to the bucket processpuzzle-store falls back to', () => {
    expect(DEFAULT_BUCKET).toBe(minio.buckets['documents']);
  });

  it('generates thumbnails to the same specification', () => {
    expect(THUMBNAIL_CONFIG.enabled).toBe(minio.thumbnail.enabled);
    expect(THUMBNAIL_CONFIG.maxDimension).toBe(minio.thumbnail['max-dimension']);
    expect(THUMBNAIL_CONFIG.quality).toBe(minio.thumbnail.quality);
  });
});

describe('findBucketName', () => {
  it('routes a known MIME type to its bucket', () => {
    expect(findBucketName('image/png')).toBe('images');
    expect(findBucketName('application/pdf')).toBe('documents');
    expect(findBucketName('text/markdown')).toBe('text');
    expect(findBucketName('application/zip')).toBe('archives');
  });

  it('falls back to documents for an unknown or absent MIME type', () => {
    expect(findBucketName('application/x-unheard-of')).toBe(DEFAULT_BUCKET);
    expect(findBucketName(undefined)).toBe(DEFAULT_BUCKET);
    expect(findBucketName('')).toBe(DEFAULT_BUCKET);
  });

  it('matches BucketNameFinder even where its normalization is quirky', () => {
    // Spring strips the non-alphanumerics from the key, so these reach the same entry...
    expect(findBucketName('image+png')).toBe('images');
    // ...while a differently-cased MIME type does not, exactly as on the Java side.
    expect(findBucketName('IMAGE/PNG')).toBe(DEFAULT_BUCKET);
  });
});

describe('object keys', () => {
  it('suffixes the thumbnail like ThumbnailNaming does', () => {
    expect(THUMBNAIL_SUFFIX).toBe('-thumb');
    expect(thumbnailKey('abc-123')).toBe('abc-123-thumb');
  });

  it('prefixes the object with its logical bucket', () => {
    expect(objectPath('images', 'abc-123')).toBe('images/abc-123');
  });
});
