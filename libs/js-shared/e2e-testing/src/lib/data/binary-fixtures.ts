import { deflateSync } from 'node:zlib';

/**
 * Binary payloads for tests that upload a file.
 *
 * Built in memory rather than read from a checked-in fixture: `setInputFiles` takes a buffer, so nothing has to
 * resolve a path relative to whichever application is running the suite, and the bytes are the same on every
 * machine. The image is generated rather than embedded because the object store's thumbnail step has to have
 * something to downscale — a 1×1 pixel would exercise the plumbing without exercising the resize.
 */

const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

const CRC_TABLE: number[] = (() => {
  const table: number[] = [];
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

function crc32(bytes: Buffer): number {
  let crc = 0xffffffff;
  for (const byte of bytes) crc = CRC_TABLE[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

/** A PNG chunk: length, type, payload, CRC over type + payload. */
function chunk(type: string, payload: Buffer): Buffer {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(payload.length);
  const typeAndPayload = Buffer.concat([Buffer.from(type, 'ascii'), payload]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(typeAndPayload));
  return Buffer.concat([length, typeAndPayload, crc]);
}

/**
 * A truecolour PNG carrying a deterministic gradient.
 *
 * Landscape by default, and larger than the 200px thumbnail box in both directions, so a thumbnail generated
 * from it is a genuine downscale.
 */
export function createPngBuffer(width = 320, height = 240): Buffer {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8; // bit depth
  header[9] = 2; // colour type: truecolour RGB
  header[10] = 0; // deflate
  header[11] = 0; // adaptive filtering
  header[12] = 0; // no interlace

  // One filter byte per scanline (0 = None), then RGB triples.
  const raw = Buffer.alloc(height * (1 + width * 3));
  let offset = 0;
  for (let y = 0; y < height; y++) {
    raw[offset++] = 0;
    for (let x = 0; x < width; x++) {
      raw[offset++] = Math.floor((x * 255) / width);
      raw[offset++] = Math.floor((y * 255) / height);
      raw[offset++] = Math.floor(((x + y) * 255) / (width + height));
    }
  }

  return Buffer.concat([PNG_SIGNATURE, chunk('IHDR', header), chunk('IDAT', deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

/** A plain-text payload — the non-image counterpart, for the branch that renders a MIME icon. */
export function createTextBuffer(text: string): Buffer {
  return Buffer.from(text, 'utf-8');
}
