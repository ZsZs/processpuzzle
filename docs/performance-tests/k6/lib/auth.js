import http from 'k6/http';
import { check } from 'k6';
import { AUTH, KEYCLOAK_URL, REALM, CLIENT_ID, USERNAME, PASSWORD } from './config.js';

// Password grant. The testbed and biz clients no longer allow Direct Access Grants, so point
// KC_CLIENT_ID at a throwaway client that does (a local/ci realm only, never stage or prod).
// Password grant against the testbed's public client. Call in setup() so the (CPU-heavy) login is
// not part of the measured API traffic; tokens last minutes, so long runs call refreshIfNeeded().
export function login(user = USERNAME, pass = PASSWORD) {
  if (!AUTH) return null;
  const res = http.post(
    `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`,
    { grant_type: 'password', client_id: CLIENT_ID, username: user, password: pass },
    { tags: { kind: 'login' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
  const body = res.json();
  return { token: body.access_token, expiresAt: Date.now() + (body.expires_in - 30) * 1000, user, pass };
}

export function refreshIfNeeded(session) {
  return session && Date.now() > session.expiresAt ? login(session.user, session.pass) : session;
}

export function headers(session, extra = {}) {
  return session ? { Authorization: `Bearer ${session.token}`, ...extra } : extra;
}
