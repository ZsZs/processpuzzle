import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { Document, DocumentInputPort, DocumentStatus, PortType } from './base-document';
import { BaseDocumentMapper } from './base-document.mapper';
import { BaseDocumentService } from './base-document.service';

describe('BaseDocumentService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: BaseDocumentService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { DOCUMENT_SERVICE_ROOT: serviceRoot } } },
        BaseDocumentMapper,
        BaseDocumentService,
      ],
    });
    service = TestBed.inject(BaseDocumentService);
    controller = TestBed.inject(HttpTestingController);
  });

  const persisted = () =>
    new Document(
      'q3-plan',
      'demo',
      'Q3 plan',
      'The plan',
      [new DocumentInputPort('customer', PortType.ENTITY_REF, true)],
      [],
      [{ locale: 'en', status: DocumentStatus.DRAFT, blockCount: 1 }],
      undefined,
      4,
    ) as PersistedEntity<Document>;

  // The reason update() is overridden at all: the inherited implementation PUTs the whole entity
  // to /documents/{id}, where updateDocument replaces the stored block list with whatever the form
  // last loaded — discarding block-level edits made through DocumentContentService meanwhile.
  it('routes a generic form save to the properties sub-resource', () => {
    service.update(persisted()).subscribe();

    const request = controller.expectOne(`${serviceRoot}/documents/q3-plan/properties`);
    expect(request.request.method).toBe('PUT');
    request.flush({ id: 'q3-plan', title: 'Q3 plan' });
  });

  it('never sends content on a properties save', () => {
    service.update(persisted()).subscribe();

    const request = controller.expectOne(`${serviceRoot}/documents/q3-plan/properties`);
    expect(request.request.body).not.toHaveProperty('blocks');
    expect(request.request.body).not.toHaveProperty('translation');
    expect(request.request.body).not.toHaveProperty('translations');
    expect(request.request.body.title).toBe('Q3 plan');
    expect(request.request.body.inputPorts).toHaveLength(1);
    request.flush({ id: 'q3-plan', title: 'Q3 plan' });
  });

  it('maps the response back into a Document', async () => {
    const pending = firstValueFrom(service.update(persisted()));

    controller.expectOne(`${serviceRoot}/documents/q3-plan/properties`).flush({
      id: 'q3-plan',
      orgKey: 'demo',
      title: 'Renamed',
      version: 5,
      translations: [{ locale: 'en', status: 'PUBLISHED_WITH_DRAFT_CHANGES', blockCount: 2 }],
    });

    const updated = await pending;
    expect(updated.title).toBe('Renamed');
    expect(updated.version).toBe(5);
    // The server's own view of which locales exist comes back and is trusted — this call did not send it.
    expect(updated.translations).toHaveLength(1);
    expect(updated.translations[0].status).toBe(DocumentStatus.PUBLISHED_WITH_DRAFT_CHANGES);
  });

  /**
   * The blocks the content editor shows can only come from here: `listDocuments` returns summaries with no
   * content, and the contract gives a document no root block list, so the locale has to be in the URL.
   */
  it('fetches one locale draft translation for the content editor', async () => {
    const pending = service.getTranslation('q3-plan', 'hu');

    const request = controller.expectOne((candidate) => candidate.url === `${serviceRoot}/documents/q3-plan/translations/hu`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('draft')).toBe('true');
    request.flush({ locale: 'hu', status: 'DRAFT', blocks: [{ id: 'intro', kind: 'TEXT' }] });

    expect((await pending).blocks).toHaveLength(1);
  });

  /** An explicit empty array, so a new draft starts blank instead of inheriting the source locale's prose. */
  it('starts a blank draft when asked to add a translation with no blocks', async () => {
    const pending = service.addTranslation('q3-plan', 'fr', []);

    const request = controller.expectOne(`${serviceRoot}/documents/q3-plan/translations`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ locale: 'fr', blocks: [] });
    request.flush({ locale: 'fr', status: 'DRAFT', blocks: [] });

    expect((await pending).locale).toBe('fr');
  });

  /** Omitting blocks is the "copy the source locale as a starting point" case the contract describes. */
  it('omits blocks entirely when none are given', () => {
    void service.addTranslation('q3-plan', 'fr');

    const request = controller.expectOne(`${serviceRoot}/documents/q3-plan/translations`);
    expect(request.request.body).toEqual({ locale: 'fr' });
    request.flush({ locale: 'fr', status: 'DRAFT', blocks: [] });
  });

  it('addresses a single document by its id on delete', () => {
    service.delete('q3-plan').subscribe();

    const request = controller.expectOne(`${serviceRoot}/documents/q3-plan`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
