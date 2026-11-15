import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { buildUrl } from 'build-url-ts';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { ENTITY_SERVICE_ROOT_KEY } from '../base-entity-definition/entity-definition.service';

/** One item of an embedded collection. Free-form, as the entity's payload is; `id` identifies it. */
export type CollectionItem = Record<string, unknown>;

/** What a read answers: the items, and the version of the object they were read from. */
export interface CollectionRead {
  items: CollectionItem[];
  /** `undefined` when the server sent no `ETag`. */
  version: string | undefined;
}

/**
 * Reads and changes **one item** of an embedded collection of an entity instance — `entities/<type>/<id>/<attr>[/<itemId>]`
 * — so that adding a round to a race sends the round, not the race.
 *
 * The object's version travels as an `ETag`: every answer carries it, and a change that names it back as `If-Match` is
 * refused with a 409 when someone else saved in between. A change that names none applies to whatever is stored.
 */
@Injectable({ providedIn: 'root' })
export class EmbeddedCollectionService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), ENTITY_SERVICE_ROOT_KEY);

  async list(entityName: string, id: string, attrName: string): Promise<CollectionRead> {
    const response = await firstValueFrom(this.http.get<CollectionItem[]>(this.url(entityName, id, attrName), { observe: 'response' }));
    return { items: response.body ?? [], version: versionOf(response) };
  }

  /** The item as stored — with the `id` the server assigned. */
  async append(entityName: string, id: string, attrName: string, item: CollectionItem): Promise<CollectionItem> {
    return (await firstValueFrom(this.http.post<CollectionItem>(this.url(entityName, id, attrName), item))) ?? item;
  }

  /** A merge patch: members present replace the stored ones, a `null` member is removed. */
  async patch(entityName: string, id: string, attrName: string, itemId: string, patch: CollectionItem, version?: string): Promise<CollectionItem> {
    const headers = version ? { 'If-Match': version } : undefined;
    return (await firstValueFrom(this.http.patch<CollectionItem>(this.url(entityName, id, attrName, itemId), patch, { headers }))) ?? patch;
  }

  async remove(entityName: string, id: string, attrName: string, itemId: string, version?: string): Promise<void> {
    const headers = version ? { 'If-Match': version } : undefined;
    await firstValueFrom(this.http.delete<void>(this.url(entityName, id, attrName, itemId), { headers }));
  }

  private url(entityName: string, id: string, attrName: string, itemId?: string): string {
    const segments = ['entities', entityName, id, attrName, ...(itemId === undefined ? [] : [itemId])].map(encodeURIComponent);
    return buildUrl(this.root, { path: segments.join('/') });
  }
}

function versionOf(response: HttpResponse<unknown>): string | undefined {
  return response.headers.get('ETag') ?? undefined;
}

/**
 * The merge patch that turns `before` into `after`: the members that changed, and `null` for those that were removed.
 * An unchanged item makes an empty patch, which the caller can skip.
 */
export function mergePatch(before: CollectionItem, after: CollectionItem): CollectionItem {
  const patch: CollectionItem = {};
  for (const [key, value] of Object.entries(after)) {
    if (key !== 'id' && JSON.stringify(value) !== JSON.stringify(before[key])) patch[key] = value;
  }
  for (const key of Object.keys(before)) {
    if (key !== 'id' && !(key in after)) patch[key] = null;
  }
  return patch;
}
