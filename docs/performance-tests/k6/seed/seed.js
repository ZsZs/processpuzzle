// Seeds database rows (and optionally files) through the REST API.
//   k6 run -e SEED_COUNT=100000 -e SEED_FILES=500 -e FILE_MB=5 seed/seed.js
// Idempotent for the definition only; every run adds more rows. For the 10%/50%/100% data-growth
// phase run it repeatedly with the delta, or seed in bulk with SQL generate_series (README).
import { check } from 'k6';
import { login } from '../lib/auth.js';
import { ensureEntityDefinition, createEntity, uploadFile } from '../lib/api.js';
import { makeFile } from '../lib/files.js';

const ROWS = Number(__ENV.SEED_COUNT || 1000);
const FILES = Number(__ENV.SEED_FILES || 0);
const VUS = Number(__ENV.SEED_VUS || 10);

export const options = {
  scenarios: {
    rows: { executor: 'shared-iterations', exec: 'rows', vus: VUS, iterations: ROWS, maxDuration: '6h' },
    ...(FILES > 0 ? { files: { executor: 'shared-iterations', exec: 'files', vus: Math.min(VUS, 5), iterations: FILES, maxDuration: '6h' } } : {}),
  },
  thresholds: { http_req_failed: ['rate<0.05'] }, // seeding is not a measurement; only catch a broken setup
};

const blob = FILES > 0 ? makeFile(Number(__ENV.FILE_MB || 5)) : null;

export function setup() {
  const session = login();
  ensureEntityDefinition(session);
  return { session };
}
export function rows(d) { check(createEntity(d.session, __ITER), { 'row 201': (r) => r.status === 201 }); }
export function files(d) { uploadFile(d.session, blob, `seed-${__VU}-${__ITER}.bin`); }
