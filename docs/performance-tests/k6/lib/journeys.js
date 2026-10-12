import { check, sleep } from 'k6';
import { createEntity, listEntities, getEntity, listDefinitions } from './api.js';

// Browse and CRUD. UNDECIDED in the strategy: read/write ratio and think time; adjust READ_RATIO.
const READ_RATIO = Number(__ENV.READ_RATIO || 0.9);

export function browseAndCrud(session, seedCount) {
  check(listDefinitions(session), { 'definitions 200': (r) => r.status === 200 });
  const page = Math.floor(Math.random() * Math.max(1, seedCount / 20));
  const list = listEntities(session, page);
  check(list, { 'list 200': (r) => r.status === 200 });
  const first = list.status === 200 ? (list.json('content.0.id') || list.json('0.id')) : null;
  if (first) check(getEntity(session, first), { 'get 200': (r) => r.status === 200 });
  if (Math.random() > READ_RATIO) check(createEntity(session, Date.now() % 1e9), { 'create 201': (r) => r.status === 201 });
  sleep(1 + Math.random() * 3); // think time
}
