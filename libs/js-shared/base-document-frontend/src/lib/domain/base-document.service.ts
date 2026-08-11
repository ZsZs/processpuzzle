import { Injectable } from '@angular/core';
import { firstValueFrom, from, Observable } from 'rxjs';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { Document, DocumentInputPort, DocumentOutputPort } from './base-document';
import { BaseDocumentMapper } from './base-document.mapper';

@Injectable({ providedIn: 'root' })
export class BaseDocumentService extends BaseEntityRestService<Document> {
  constructor(protected override entityMapper: BaseDocumentMapper) {
    super(entityMapper, 'DOCUMENT_SERVICE_ROOT', 'documents');
  }

  /**
   * Every save from the generic form arrives here, and is redirected to
   * {@link updateProperties} instead of the inherited `PUT /documents/{id}`. The base
   * implementation serializes the whole entity through `toDto()`, and `updateDocument` replaces
   * the stored block list wholesale — so a Properties save would overwrite `blocks` with whatever
   * the form happened to load, discarding every block-level edit `DocumentContentService` made in
   * the meantime. Routing through the properties endpoint makes that structurally impossible
   * rather than merely unlikely: `DocumentPropertiesInput` has no blocks field.
   *
   * This is the interception point precisely because the store calls `service.update()` — the
   * form is rendered through a `router-outlet`, so there is no component binding between it and
   * a container that could intervene instead.
   */
  override update(entity: PersistedEntity<Document>): Observable<PersistedEntity<Document>> {
    return from(this.updateProperties(entity.id, entity.title, entity.description, entity.inputPorts, entity.outputPorts));
  }

  /** Hits `PUT .../documents/{id}/properties`. Also callable directly, outside the form flow. */
  async updateProperties(id: string, title: string, description: string | undefined,
                          inputPorts: DocumentInputPort[], outputPorts: DocumentOutputPort[]): Promise<PersistedEntity<Document>> {
    const pathParams = new Map<string, string>([['id', id]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/properties', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');

    const body = { title, description, inputPorts, outputPorts };
    const dto = await firstValueFrom(this.httpClient.put(fullUrl, body, { headers: this.headers }));
    return this.entityMapper.fromDto(dto) as PersistedEntity<Document>;
  }
}
