import { Injectable } from '@angular/core';
import { firstValueFrom, from, Observable } from 'rxjs';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { Document, DocumentBlock, DocumentInputPort, DocumentOutputPort, DocumentTranslation } from './base-document';
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

  /**
   * One locale's block list, fetched on its own rather than read off an entity in the store: `listDocuments`
   * returns summaries with no block content, and the store loads through exactly that call, so a document
   * held in the store never has blocks to read. Rejects with a 404 when the locale has no translation —
   * see {@link addTranslation}, and DocumentContentTabComponent for how the editor treats that case.
   */
  async getTranslation(documentId: string, locale: string, draft = true): Promise<DocumentTranslation> {
    const fullUrl = this.translationUrl(documentId, locale);
    return firstValueFrom(this.httpClient.get<DocumentTranslation>(fullUrl, { headers: this.headers, params: { draft } }));
  }

  /**
   * Starts a draft in a locale that has none. `blocks` left undefined makes the server copy the source
   * locale's current draft as a starting point — which is what a translator wants — while an explicit empty
   * array starts blank. The editor passes an empty array: a first block is about to be appended to it, and
   * silently inheriting another locale's prose is not what "add a text block here" means.
   */
  async addTranslation(documentId: string, locale: string, blocks?: DocumentBlock[]): Promise<DocumentTranslation> {
    const pathParams = new Map<string, string>([['id', documentId]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/translations', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');

    return firstValueFrom(this.httpClient.post<DocumentTranslation>(fullUrl, { locale, ...(blocks ? { blocks } : {}) }, { headers: this.headers }));
  }

  private translationUrl(documentId: string, locale: string): string {
    const pathParams = new Map<string, string>([
      ['id', documentId],
      ['locale', locale],
    ]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/translations/%{locale}', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');
    return fullUrl;
  }
}
