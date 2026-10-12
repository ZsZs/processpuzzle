// Random-content files for upload tests. Build ONCE in the init context (outside default()) and reuse:
// generating megabytes per iteration would make the load generator the bottleneck.
// Random bytes per size, so MinIO/nginx cannot benefit from compressible content.
export function makeFile(megabytes) {
  const bytes = new Uint8Array(Math.round(megabytes * 1024 * 1024));
  for (let i = 0; i < bytes.length; i += 4) {
    const r = (Math.random() * 0xffffffff) >>> 0;
    bytes[i] = r; bytes[i + 1] = r >>> 8; bytes[i + 2] = r >>> 16; bytes[i + 3] = r >>> 24;
  }
  return bytes.buffer;
}
export const SIZES_MB = (__ENV.SIZES_MB || '1,5,10').split(',').map(Number);
