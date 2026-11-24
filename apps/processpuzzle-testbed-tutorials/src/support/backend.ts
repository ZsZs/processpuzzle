import type { APIRequestContext } from '@playwright/test';

const ENVIRONMENT = process.env['ENVIRONMENT'] ?? 'ci';

export interface ServiceRoots {
  /** Organization-scoped: `<host>/organizations/<orgKey>`. */
  backend: string;
  objectStore: string;
}

/**
 * The backend's roots, read from the frontend's own run-time configuration exactly as the application reads them —
 * `config.common.json` overlaid by `config.<ENVIRONMENT>.json`. The same reading as the e2e suite's `serviceRoots`
 * (apps/processpuzzle-testbed-e2e/src/support/race-sample.api.ts), repeated because one application may not import
 * from another.
 */
export async function serviceRoots(request: APIRequestContext, baseURL: string): Promise<ServiceRoots> {
  const base = baseURL.endsWith('/') ? baseURL : `${baseURL}/`;
  const read = async (file: string) => {
    const response = await request.get(new URL(`run-time-conf/${file}`, base).toString());
    return response.ok() ? ((await response.json())?.BASE_CONFIGURATION ?? {}) : {};
  };
  const config = { ...(await read('config.common.json')), ...(await read(`config.${ENVIRONMENT}.json`)) };
  const absolute = (root: string) => new URL(root, base).toString().replace(/\/$/, '');
  return { backend: absolute(config.BACKEND_SERVICE_ROOT), objectStore: absolute(config.OBJECT_STORE_SERVICE_ROOT) };
}
