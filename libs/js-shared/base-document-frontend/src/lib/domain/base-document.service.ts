import { Injectable } from '@angular/core';
import { firstValueFrom, from, Observable } from 'rxjs';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { Document, DocumentBlock, DocumentProperties, DocumentSignature, DocumentSignatureSubject, DocumentTranslation } from './base-document';
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
    return from(this.updateProperties(entity.id, entity));
  }

  /**
   * Hits `PUT .../documents/{id}/properties`. Also callable directly, outside the form flow.
   *
   * Takes the whole properties object rather than a field list, because the endpoint *replaces* the properties
   * block: a body short of a field blanks it, and one short of `slug` or `sourceLocale` — both required — is a
   * 400. Naming twelve parameters would make the next field added to the contract a silent omission here;
   * taking the object makes it a compile error in {@link Document} instead.
   */
  async updateProperties(id: string, properties: DocumentProperties): Promise<PersistedEntity<Document>> {
    const pathParams = new Map<string, string>([['id', id]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/properties', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');

    // Spelled out rather than spread from `properties`, so that `translations`, `translation` and the audit
    // fields a Document also carries cannot reach a body the contract describes as having none of them.
    const body: DocumentProperties = {
      slug: properties.slug,
      title: properties.title,
      subject: properties.subject,
      description: properties.description,
      author: properties.author,
      sourceLocale: properties.sourceLocale,
      isPublic: properties.isPublic,
      readerRoles: properties.readerRoles,
      editorRoles: properties.editorRoles,
      publisherRoles: properties.publisherRoles,
      inputPorts: properties.inputPorts,
      outputPorts: properties.outputPorts,
      kind: properties.kind,
    };
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

  /** `POST .../translations/{locale}/publish` — makes the locale's current draft what readers see. Publisher role. */
  async publishTranslation(documentId: string, locale: string): Promise<DocumentTranslation> {
    return firstValueFrom(this.httpClient.post<DocumentTranslation>(`${this.translationUrl(documentId, locale)}/publish`, null, { headers: this.headers }));
  }

  /**
   * `POST .../documents/{id}/archive` — stores a snapshot of the document and locks it from further edits.
   * Answers the document with its `archive`. Publisher role.
   */
  async archive(documentId: string): Promise<PersistedEntity<Document>> {
    const pathParams = new Map<string, string>([['id', documentId]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/archive', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');

    const dto = await firstValueFrom(this.httpClient.post(fullUrl, null, { headers: this.headers }));
    return this.entityMapper.fromDto(dto) as PersistedEntity<Document>;
  }

  /**
   * `POST .../documents/{id}/signatures` — signs the published content of `locale` (the source locale when
   * omitted) for `subject`. The server answers an existing signature for a repeat rather than a second one,
   * so calling this twice is safe.
   */
  async sign(documentId: string, subject: DocumentSignatureSubject, locale?: string): Promise<DocumentSignature> {
    const body = { subjectType: subject.subjectType, subjectId: subject.subjectId, ...(locale ? { locale } : {}) };
    return firstValueFrom(this.httpClient.post<DocumentSignature>(this.signaturesUrl(documentId), body, { headers: this.headers }));
  }

  /** `GET .../documents/{id}/signatures`, newest first — narrowed to `subject` when given. */
  async listSignatures(documentId: string, subject?: DocumentSignatureSubject): Promise<DocumentSignature[]> {
    const params: Record<string, string> = subject ? { subjectType: subject.subjectType, subjectId: subject.subjectId } : {};
    return firstValueFrom(this.httpClient.get<DocumentSignature[]>(this.signaturesUrl(documentId), { headers: this.headers, params }));
  }

  /**
   * `PUT .../signatures/{id}/pdf` — the second phase of signing: uploads the rendered report. Allowed once, and
   * only to the signer; the answer is the signature with its `pdf`. No `headers`: the JSON content type of the
   * base class would replace the multipart boundary the browser sets.
   */
  async attachPdf(documentId: string, signatureId: string, pdf: Blob, fileName: string): Promise<DocumentSignature> {
    const body = new FormData();
    body.append('file', pdf, fileName);
    return firstValueFrom(this.httpClient.put<DocumentSignature>(`${this.signaturesUrl(documentId)}/${encodeURIComponent(signatureId)}/pdf`, body));
  }

  private signaturesUrl(documentId: string): string {
    const pathParams = new Map<string, string>([['id', documentId]]);
    const fullUrl = this.buildFullUrl(this.resourceUrl + '/%{id}/signatures', { pathParams });
    if (!fullUrl) throw new Error('Could not determine the full url');
    return fullUrl;
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
