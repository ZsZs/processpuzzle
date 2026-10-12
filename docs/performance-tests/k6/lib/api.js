import http from 'k6/http';
import { check } from 'k6';
import { API, ORG_KEY, ENTITY_CODE } from './config.js';
import { headers } from './auth.js';

const org = `${API}/organizations/${ORG_KEY}`;
const api = { kind: 'api' };

export function ensureEntityDefinition(session) {
  const body = {
    code: ENTITY_CODE, name: 'Perf item', isEmbedded: false, status: 'ACTIVE',
    attributes: [
      { code: 'title', name: 'Title', valueKind: 'TEXT', formControlType: 'TEXT', required: true, indexed: true },
      { code: 'quantity', name: 'Quantity', valueKind: 'NUMBER', formControlType: 'NUMBER', indexed: true },
      { code: 'notes', name: 'Notes', valueKind: 'TEXT', formControlType: 'TEXTAREA' },
    ],
  };
  const res = http.post(`${org}/entity-definitions`, JSON.stringify(body),
    { headers: headers(session, { 'Content-Type': 'application/json' }), tags: { kind: 'setup' } });
  check(res, { 'definition created or exists': (r) => r.status === 201 || r.status === 409 });
}

export function createEntity(session, i) {
  const payload = { title: `perf-${i}`, quantity: i % 1000, notes: 'x'.repeat(200) };
  return http.post(`${org}/entities/${ENTITY_CODE}`, JSON.stringify({ payload }),
    { headers: headers(session, { 'Content-Type': 'application/json' }), tags: api });
}

export function listEntities(session, page = 0, size = 20, rsql) {
  const q = `page=${page}&size=${size}` + (rsql ? `&rsql=${encodeURIComponent(rsql)}` : '');
  return http.get(`${org}/entities/${ENTITY_CODE}?${q}`, { headers: headers(session), tags: api });
}

export function getEntity(session, id) {
  return http.get(`${org}/entities/${ENTITY_CODE}/${id}`, { headers: headers(session), tags: api });
}

export function listDefinitions(session) {
  return http.get(`${org}/entity-definitions?page=0&size=50`, { headers: headers(session), tags: api });
}

// Multipart upload through nginx and the backend (what browsers do). Tagged separately because the
// latency target excludes uploads. `data` is an ArrayBuffer made once in init (never per iteration).
export function uploadFile(session, data, name, mimeType = 'application/octet-stream') {
  const res = http.post(`${API}/objects`,
    { file: http.file(data, name, mimeType), name, mimeType },
    { headers: headers(session), tags: { kind: 'upload' }, timeout: '120s' });
  check(res, { 'upload 201': (r) => r.status === 201 });
  return res;
}

export function download(session, bucket, objectId) {
  // Presigned GET: the API answers with a URI that points at MinIO.
  const res = http.get(`${API}/objects/${bucket}/${objectId}/uri`, { headers: headers(session), tags: api });
  if (res.status !== 200) return res;
  const uri = res.json('uri') || res.body.replace(/"/g, '');
  return http.get(uri, { tags: { kind: 'download' }, timeout: '120s' });
}
