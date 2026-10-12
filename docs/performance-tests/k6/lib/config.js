// Central configuration, all overridable with  k6 run -e NAME=value  (or env vars).
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:9090'; // nginx: Angular + /api
export const API = `${BASE_URL}/api/v1`;
export const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:7070';
export const REALM = __ENV.KC_REALM || 'processpuzzle-testbed';
export const CLIENT_ID = __ENV.KC_CLIENT_ID || 'processpuzzle-testbed';
export const USERNAME = __ENV.KC_USER || 'test-user';
export const PASSWORD = __ENV.KC_PASSWORD || 'test';
// UNVERIFIED: the tenant key the testbed serves; check the realm/app config and set ORG_KEY.
export const ORG_KEY = __ENV.ORG_KEY || 'processpuzzle-testbed';
export const AUTH = (__ENV.AUTH || 'off') === 'on'; // on = PERF_REQUIRE_AUTHENTICATION=true
export const ENTITY_CODE = __ENV.ENTITY_CODE || 'perf_item';
export const SEED_PREFIX = 'perf-';

// Success thresholds from the strategy doc ("Success criteria").
export const apiThresholds = {
  http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '30s' }],
  'http_req_duration{kind:api}': [{ threshold: 'p(95)<500', abortOnFail: true, delayAbortEval: '60s' }],
};
