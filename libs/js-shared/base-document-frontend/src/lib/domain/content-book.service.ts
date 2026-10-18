import { Injectable } from '@angular/core';
import { BaseEntity, BaseEntityLoadResponse, BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { firstValueFrom } from 'rxjs';
import { ContentBook, ContentBookNode } from './content-book';
import { ContentBookMapper } from './content-book.mapper';

@Injectable({ providedIn: 'root' })
export class ContentBookService extends BaseEntityRestService<ContentBook> {
  constructor(protected override entityMapper: ContentBookMapper) {
    super(entityMapper, 'DOCUMENT_SERVICE_ROOT', 'content-books');
  }

  /** The book a widget is pointed at, by its slug; `undefined` when the organization has none of that name, or it may not be read. */
  async findBySlug(slug: string): Promise<PersistedEntity<ContentBook> | undefined> {
    const response = await firstValueFrom(this.findByQuery({ query: `slug==${slug}`, pageSize: 1 }));
    return contentOf(response)[0];
  }

  /** `PUT .../content-books/{id}/nodes` — the whole tree in one write, answered with the book as stored. */
  async replaceNodes(id: string, nodes: ContentBookNode[]): Promise<PersistedEntity<ContentBook>> {
    const pathParams = new Map<string, string>([['id', id]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/nodes', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');

    const body = { nodes: nodes.map(({ id: nodeId, documentId, parentId, orderIndex }) => ({ id: nodeId, documentId, parentId: parentId ?? null, orderIndex })) };
    const dto = await firstValueFrom(this.httpClient.put(fullUrl, body, { headers: this.headers }));
    return this.entityMapper.fromDto(dto) as PersistedEntity<ContentBook>;
  }
}

/** The entities of any of the three shapes `findByQuery` answers with. */
export function contentOf<T extends BaseEntity>(response: BaseEntityLoadResponse<T> | T[] | T): T[] {
  if (Array.isArray(response)) return response;
  if (response && typeof response === 'object' && Array.isArray((response as BaseEntityLoadResponse<T>).content)) return (response as BaseEntityLoadResponse<T>).content;
  return response ? [response as T] : [];
}
