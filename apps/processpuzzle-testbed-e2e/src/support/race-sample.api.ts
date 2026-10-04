import { APIRequestContext, expect } from '@playwright/test';

/**
 * REST access to the race sample's data — the setup and the assertions of the recognition e2e tests, so that
 * a test arranges its own races and entries instead of depending on what a database happens to hold, and
 * checks what was *saved*, not only what the page shows.
 *
 * The roots are read from the frontend's own run-time configuration, exactly as the application reads them —
 * `config.common.json` overlaid by `config.<ENVIRONMENT>.json` — so the tests reach the same backend on dev
 * (:8080 directly), ci and stage (`/api` behind the frontend's reverse proxy).
 */

export interface EntityObject<P = Record<string, unknown>> {
  id: string;
  version?: number;
  payload: P;
}

export interface ServiceRoots {
  /** Organization-scoped: `<host>/organizations/<orgKey>`. */
  backend: string;
  objectStore: string;
}

export interface Artifact {
  bucket: string;
  objectId: string;
  name: string;
  mimeType: string;
}

const ENVIRONMENT = process.env['ENVIRONMENT'] || 'dev';

export async function serviceRoots(request: APIRequestContext, baseURL: string): Promise<ServiceRoots> {
  const read = async (file: string) => {
    const response = await request.get(new URL(`run-time-conf/${file}`, withSlash(baseURL)).toString());
    return response.ok() ? ((await response.json())?.BASE_CONFIGURATION ?? {}) : {};
  };
  const config = { ...(await read('config.common.json')), ...(await read(`config.${ENVIRONMENT}.json`)) };
  const absolute = (root: string) => new URL(root, withSlash(baseURL)).toString().replace(/\/$/, '');
  return { backend: absolute(config.BACKEND_SERVICE_ROOT), objectStore: absolute(config.OBJECT_STORE_SERVICE_ROOT) };
}

/** Base-entity objects of one definition, created and removed through the REST API. */
export class EntityApi {
  private readonly created: { code: string; id: string }[] = [];

  constructor(
    private readonly request: APIRequestContext,
    private readonly roots: ServiceRoots,
  ) {}

  async list<P>(code: string, rsql?: string): Promise<EntityObject<P>[]> {
    const params: Record<string, string | number> = { size: 200 };
    if (rsql) params['rsql'] = rsql;
    const response = await this.request.get(this.url(code), { params });
    expect(response.ok(), `listing ${code}: ${response.status()}`).toBe(true);
    return (await response.json()).content ?? [];
  }

  /** The one object of `code` whose `attribute` equals `value`; fails when there is none. */
  async findBy<P>(code: string, attribute: string, value: string): Promise<EntityObject<P>> {
    const found = (await this.list<P>(code)).find((object) => (object.payload as Record<string, unknown>)[attribute] === value);
    expect(found, `no ${code} with ${attribute} = ${value}`).toBeDefined();
    return found as EntityObject<P>;
  }

  /** Creates the object and remembers it for {@link removeAll}. */
  async create<P extends object>(code: string, payload: P): Promise<EntityObject<P>> {
    const response = await this.request.post(this.url(code), { data: { entityDefinitionCode: code, payload } });
    expect(response.status(), `creating ${code}: ${await response.text()}`).toBe(201);
    const created = (await response.json()) as EntityObject<P>;
    this.created.push({ code, id: created.id });
    return created;
  }

  async remove(code: string, id: string): Promise<void> {
    const response = await this.request.delete(this.url(code, id));
    expect([204, 404], `deleting ${code}/${id}`).toContain(response.status());
  }

  /**
   * Removes everything this instance created, newest first — so dependants go before what they reference —
   * and the given extra objects before those, e.g. observations the page saved.
   */
  async removeAll(extra: { code: string; id: string }[] = []): Promise<void> {
    for (const { code, id } of [...extra, ...this.created.reverse()]) await this.remove(code, id);
    this.created.length = 0;
  }

  url(code: string, id?: string): string {
    return `${this.roots.backend}/entities/${code}${id ? `/${encodeURIComponent(id)}` : ''}`;
  }
}

/** Puts a file into processpuzzle-store the way the ARTIFACT control does, answering the value the attribute holds. */
export async function uploadArtifact(request: APIRequestContext, roots: ServiceRoots, name: string, mimeType: string, data: Buffer): Promise<Artifact> {
  const response = await request.post(`${roots.objectStore}/objects`, {
    multipart: { file: { name, mimeType, buffer: data }, name, mimeType },
  });
  expect(response.ok(), `uploading ${name}: ${response.status()}`).toBe(true);
  const body = await response.json();
  return { bucket: response.headers()['location'] ?? body.bucketName ?? '', objectId: body.objectID, name, mimeType };
}

export async function deleteArtifact(request: APIRequestContext, roots: ServiceRoots, artifact: Artifact): Promise<void> {
  await request.delete(`${roots.objectStore}/objects/${encodeURIComponent(artifact.bucket)}/${encodeURIComponent(artifact.objectId)}`);
}

function withSlash(url: string): string {
  return url.endsWith('/') ? url : `${url}/`;
}
