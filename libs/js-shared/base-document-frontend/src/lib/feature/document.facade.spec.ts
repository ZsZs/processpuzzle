import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { Document } from '../domain/base-document';
import { BaseDocumentMapper } from '../domain/base-document.mapper';
import { BaseDocumentService } from '../domain/base-document.service';
import { BaseDocumentStore } from '../domain/base-document.store';
import { DocumentFacade } from './document.facade';

describe('DocumentFacade', () => {
  let facade: DocumentFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { DOCUMENT_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        DocumentFacade,
      ],
    });
    facade = TestBed.inject(DocumentFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(Document);
    expect(facade.entityName).toBe('Document');
  });

  /**
   * All three deliberately the root-provided instances: the container component injects `BaseDocumentStore`
   * directly to feed the content editor, so a store minted per facade would leave the two halves of the same
   * screen reading different state — and `BaseDocumentService` is what redirects the form's save to
   * `/properties` instead of putting the whole entity back over the blocks.
   */
  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(BaseDocumentMapper));
    expect(facade.service).toBe(TestBed.inject(BaseDocumentService));
    expect(facade.storeClass).toBe(BaseDocumentStore);
    expect(facade.store).toBe(TestBed.inject(BaseDocumentStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
    expect(facade.attrDescriptors.length).toBeGreaterThan(0);
  });
});
