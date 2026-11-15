import { HttpClient, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { buildUrl } from 'build-url-ts';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { ENTITY_SERVICE_ROOT_KEY } from '../base-entity-definition/entity-definition.service';

/**
 * One item of an embedded collection. Free-form, as the entity's payload is. `id` identifies it, and `itemVersion` counts
 * the changes made through the item endpoints — the server keeps both, so neither is sent back as a change.
 */
export type CollectionItem = Record<string, unknown>;

/** The members the server keeps on an item; a merge patch never carries them. */
const SERVER_MEMBERS = new Set(['id', 'itemVersion']);

/** The item's version as an `If-Match` value, or `undefined` for an item stored before versions existed. */
export function itemVersionOf(item: CollectionItem): string | undefined {
  return typeof item['itemVersion'] === 'number' ? String(item['itemVersion']) : undefined;
}

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

  /**
   * A merge patch: members present replace the stored ones, a `null` member is removed. `version` is the item's own version
   * as last read; the server refuses the change with a 409 when the item has changed since.
   */
  async patch(entityName: string, id: string, attrName: string, itemId: string, patch: CollectionItem, version?: string): Promise<CollectionItem> {
    const headers = version ? { 'If-Match': version } : undefined;
    return (await firstValueFrom(this.http.patch<CollectionItem>(this.url(entityName, id, attrName, itemId), patch, { headers }))) ?? patch;
  }

  async remove(entityName: string, id: string, attrName: string, itemId: string, version?: string): Promise<void> {
    const headers = version ? { 'If-Match': version } : undefined;
    await firstValueFrom(this.http.delete<void>(this.url(entityName, id, attrName, itemId), { headers }));
  }

  /**
   * A collection nested in an item of another: the `childAttrName` collection of the item `itemId` of `attrName`. The same
   * operations as above, one level down.
   */
  async listNested(entityName: string, id: string, attrName: string, itemId: string, childAttrName: string): Promise<CollectionRead> {
    const response = await firstValueFrom(this.http.get<CollectionItem[]>(this.url(entityName, id, attrName, itemId, childAttrName), { observe: 'response' }));
    return { items: response.body ?? [], version: versionOf(response) };
  }

  async appendNested(entityName: string, id: string, attrName: string, itemId: string, childAttrName: string, item: CollectionItem): Promise<CollectionItem> {
    return (await firstValueFrom(this.http.post<CollectionItem>(this.url(entityName, id, attrName, itemId, childAttrName), item))) ?? item;
  }

  async patchNested(entityName: string, id: string, attrName: string, itemId: string, childAttrName: string, childItemId: string, patch: CollectionItem, version?: string): Promise<CollectionItem> {
    const headers = version ? { 'If-Match': version } : undefined;
    return (await firstValueFrom(this.http.patch<CollectionItem>(this.url(entityName, id, attrName, itemId, childAttrName, childItemId), patch, { headers }))) ?? patch;
  }

  async removeNested(entityName: string, id: string, attrName: string, itemId: string, childAttrName: string, childItemId: string, version?: string): Promise<void> {
    const headers = version ? { 'If-Match': version } : undefined;
    await firstValueFrom(this.http.delete<void>(this.url(entityName, id, attrName, itemId, childAttrName, childItemId), { headers }));
  }

  /** The URL of the collection or item at the given path: `attrName`, then alternately an item id and an attribute. */
  private url(entityName: string, id: string, ...path: string[]): string {
    const segments = ['entities', entityName, id, ...path].map(encodeURIComponent);
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
    if (!SERVER_MEMBERS.has(key) && JSON.stringify(value) !== JSON.stringify(before[key])) patch[key] = value;
  }
  for (const key of Object.keys(before)) {
    if (!SERVER_MEMBERS.has(key) && !(key in after)) patch[key] = null;
  }
  return patch;
}
