import sharp from 'sharp';

/**
 * Firebase pendant of `ThumbnailGenerator`, which uses Thumbnailator with the same
 * max-dimension box, JPEG output and quality. `rotate()` applies the EXIF orientation
 * so portrait photos are not thumbnailed sideways, and `withoutEnlargement` leaves
 * images already smaller than the box alone instead of upscaling them.
 */
export async function generateThumbnail(source: Buffer, maxDimension: number, quality: number): Promise<Buffer> {
  return sharp(source)
    .rotate()
    .resize({ width: maxDimension, height: maxDimension, fit: 'inside', withoutEnlargement: true })
    .jpeg({ quality: Math.round(quality * 100) })
    .toBuffer();
}

/** SVG is excluded for the same reason as in `UploadObject`: it is not a raster source. */
export function isThumbnailable(mimeType: string | undefined): boolean {
  if (!mimeType) return false;
  const normalized = mimeType.toLowerCase();
  return normalized.startsWith('image/') && normalized !== 'image/svg+xml';
}
