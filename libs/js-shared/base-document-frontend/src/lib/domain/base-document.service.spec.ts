import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { Document, DocumentInputPort, BlockKind, PortType } from './base-document';
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
      [{ id: 'chart-1', kind: BlockKind.WIDGET, type: 'entity-grid' }],
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

  it('never sends blocks on a properties save, even though the entity carries them', () => {
    service.update(persisted()).subscribe();

    const request = controller.expectOne(`${serviceRoot}/documents/q3-plan/properties`);
    expect(request.request.body).not.toHaveProperty('blocks');
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
      blocks: [{ id: 'chart-1', kind: 'WIDGET' }],
    });

    const updated = await pending;
    expect(updated.title).toBe('Renamed');
    expect(updated.version).toBe(5);
    // The server's own view of the blocks comes back and is trusted — this call did not send them.
    expect(updated.blocks).toHaveLength(1);
  });

  it('addresses a single document by its id on delete', () => {
    service.delete('q3-plan').subscribe();

    const request = controller.expectOne(`${serviceRoot}/documents/q3-plan`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
