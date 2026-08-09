import { describe, expect, it } from 'vitest';
import { inflateSync } from 'node:zlib';
import { createPngBuffer, createTextBuffer } from './binary-fixtures';

/**
 * These payloads are uploaded to a real object store, which decodes them: `processpuzzle-store` runs the image
 * through Thumbnailator to derive a preview. A fixture that is merely "some bytes" would fail there rather than
 * here, in an e2e run, with the store's stack trace instead of a diff — so the structure is asserted here, where
 * a break is legible.
 */

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

/** Walks the chunk stream, yielding `[type, payload]` and verifying each chunk's CRC as it goes. */
function readChunks(png: Buffer): Array<[string, Buffer]> {
  const chunks: Array<[string, Buffer]> = [];
  let offset = PNG_SIGNATURE.length;

  while (offset < png.length) {
    const length = png.readUInt32BE(offset);
    const type = png.toString('ascii', offset + 4, offset + 8);
    const payload = png.subarray(offset + 8, offset + 8 + length);
    const declaredCrc = png.readUInt32BE(offset + 8 + length);

    expect(declaredCrc, `CRC of the ${type} chunk`).toBe(crc32(png.subarray(offset + 4, offset + 8 + length)));
    chunks.push([type, payload]);
    offset += 12 + length;
  }

  return chunks;
}

/** An independent implementation of the checksum, so the test does not confirm the fixture against itself. */
function crc32(bytes: Buffer): number {
  let crc = 0xffffffff;
  for (const byte of bytes) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit++) crc = crc & 1 ? (crc >>> 1) ^ 0xedb88320 : crc >>> 1;
  }
  return (crc ^ 0xffffffff) >>> 0;
}

describe('createPngBuffer', () => {
  it('starts with the PNG signature', () => {
    expect(createPngBuffer().subarray(0, 8)).toEqual(PNG_SIGNATURE);
  });

  it('emits IHDR, IDAT and IEND in that order, each with a valid CRC', () => {
    const types = readChunks(createPngBuffer()).map(([type]) => type);

    expect(types).toEqual(['IHDR', 'IDAT', 'IEND']);
  });

  it('declares the requested dimensions as 8-bit truecolour, undeflated and uninterlaced', () => {
    const [, header] = readChunks(createPngBuffer(64, 32))[0];

    expect(header.readUInt32BE(0)).toBe(64);
    expect(header.readUInt32BE(4)).toBe(32);
    expect(header[8], 'bit depth').toBe(8);
    expect(header[9], 'colour type: truecolour RGB').toBe(2);
    expect([header[10], header[11], header[12]], 'compression, filter, interlace').toEqual([0, 0, 0]);
  });

  it('carries one filter byte and three colour bytes per pixel in the compressed data', () => {
    const [, idat] = readChunks(createPngBuffer(64, 32))[1];

    // A scanline is `1 + width * 3` bytes: the filter type, then RGB triples.
    expect(inflateSync(idat).length).toBe(32 * (1 + 64 * 3));
  });

  it('filters every scanline with type 0, which is what makes the pixel bytes literal', () => {
    const raw = inflateSync(readChunks(createPngBuffer(8, 4))[1][1]);
    const stride = 1 + 8 * 3;

    const filterBytes = Array.from({ length: 4 }, (_, y) => raw[y * stride]);
    expect(filterBytes).toEqual([0, 0, 0, 0]);
  });

  it('writes a gradient rather than a flat fill, so a downscale has something to resample', () => {
    const raw = inflateSync(readChunks(createPngBuffer(8, 4))[1][1]);
    const stride = 1 + 8 * 3;

    const firstPixel = raw.subarray(1, 4);
    const lastPixelOfFirstRow = raw.subarray(stride - 3, stride);
    expect(firstPixel).not.toEqual(lastPixelOfFirstRow);
  });

  it('defaults to 320x240 — larger than the 200px thumbnail box in both directions, so the resize is real', () => {
    const [, header] = readChunks(createPngBuffer())[0];

    expect([header.readUInt32BE(0), header.readUInt32BE(4)]).toEqual([320, 240]);
  });
});

describe('createTextBuffer', () => {
  it('encodes the text as UTF-8', () => {
    expect(createTextBuffer('artifact fixture').toString('utf-8')).toBe('artifact fixture');
  });

  it('encodes non-ASCII as more bytes than characters', () => {
    expect(createTextBuffer('árvíztűrő').length).toBeGreaterThan('árvíztűrő'.length);
  });
});
