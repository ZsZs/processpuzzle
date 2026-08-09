import { describe, expect, it } from 'vitest';
import sharp from 'sharp';
import { generateThumbnail, isThumbnailable } from './thumbnail-generator.js';

function image(width: number, height: number): Promise<Buffer> {
  return sharp({ create: { width, height, channels: 3, background: '#3366aa' } })
    .png()
    .toBuffer();
}

describe('generateThumbnail', () => {
  it('fits a landscape image into the box and emits JPEG', async () => {
    const thumbnail = await sharp(await generateThumbnail(await image(640, 480), 200, 0.85)).metadata();

    expect(thumbnail.format).toBe('jpeg');
    expect(thumbnail.width).toBe(200);
    expect(thumbnail.height).toBe(150);
  });

  it('fits a portrait image by its longer side', async () => {
    const thumbnail = await sharp(await generateThumbnail(await image(300, 900), 200, 0.85)).metadata();

    expect(thumbnail.width).toBe(67);
    expect(thumbnail.height).toBe(200);
  });

  it('leaves an image smaller than the box at its original size', async () => {
    const thumbnail = await sharp(await generateThumbnail(await image(80, 60), 200, 0.85)).metadata();

    expect(thumbnail.width).toBe(80);
    expect(thumbnail.height).toBe(60);
  });

  it('produces a smaller file at a lower quality', async () => {
    // A flat colour compresses to the same bytes at any quality — the knob only shows on
    // high-entropy content, so this uses deterministic pseudo-random pixels.
    const pixels = Buffer.alloc(400 * 400 * 3);
    for (let i = 0; i < pixels.length; i++) pixels[i] = (i * 2654435761) % 256;
    const source = await sharp(pixels, { raw: { width: 400, height: 400, channels: 3 } })
      .png()
      .toBuffer();

    const high = await generateThumbnail(source, 200, 0.9);
    const low = await generateThumbnail(source, 200, 0.2);

    expect(low.length).toBeLessThan(high.length);
  });

  it('rejects a buffer that is not an image', async () => {
    await expect(generateThumbnail(Buffer.from('not an image'), 200, 0.85)).rejects.toThrow();
  });
});

describe('isThumbnailable', () => {
  it('accepts raster images regardless of case', () => {
    expect(isThumbnailable('image/png')).toBe(true);
    expect(isThumbnailable('IMAGE/JPEG')).toBe(true);
  });

  it('rejects SVG, since it is not a raster source', () => {
    expect(isThumbnailable('image/svg+xml')).toBe(false);
    expect(isThumbnailable('IMAGE/SVG+XML')).toBe(false);
  });

  it('rejects non-images and an absent MIME type', () => {
    expect(isThumbnailable('application/pdf')).toBe(false);
    expect(isThumbnailable('text/plain')).toBe(false);
    expect(isThumbnailable(undefined)).toBe(false);
    expect(isThumbnailable('')).toBe(false);
  });
});
