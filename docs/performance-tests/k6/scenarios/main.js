// One entry point; pick the phase with  -e PHASE=baseline|users|stress|spike|soak|uploads
import { check } from 'k6';
import { login, refreshIfNeeded } from '../lib/auth.js';
import { apiThresholds } from '../lib/config.js';
import { browseAndCrud } from '../lib/journeys.js';
import { uploadFile } from '../lib/api.js';
import { makeFile, SIZES_MB } from '../lib/files.js';
import { userSteps, stress, spike, soak } from '../lib/profiles.js';

const PHASE = __ENV.PHASE || 'baseline';
const SEED_COUNT = Number(__ENV.SEED_COUNT || 1000);

const files = SIZES_MB.map((mb) => ({ mb, data: makeFile(mb) })); // init: once per VU, mind VU memory

const phases = {
  baseline: { executor: 'constant-vus', exec: 'browse', vus: 2, duration: '3m' },
  users: userSteps('browse'),
  stress: stress('browse'),
  spike: spike('browse'),
  soak: soak('browse', Number(__ENV.SOAK_VUS || 100)),
  uploads: { executor: 'ramping-arrival-rate', exec: 'upload', startRate: 1, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 400,
    stages: [{ duration: '2m', target: 5 }, { duration: '5m', target: 5 }, { duration: '2m', target: 20 }, { duration: '5m', target: 20 }] },
};

export const options = {
  scenarios: { [PHASE]: phases[PHASE] },
  thresholds: apiThresholds,
  summaryTrendStats: ['avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() { return { session: login() }; }

export function browse(data) {
  data.session = refreshIfNeeded(data.session);
  browseAndCrud(data.session, SEED_COUNT);
}

export function upload(data) {
  const f = files[Math.floor(Math.random() * files.length)];
  check(uploadFile(refreshIfNeeded(data.session), f.data, `perf-${f.mb}mb-${__VU}-${__ITER}.bin`), { 'ok': (r) => r.status === 201 });
}
