import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { DEFINITION_REVISION, definitionRead, DefinitionRevision, definitionRevisionInterceptor } from './definition-revision';

describe('definitionRevisionInterceptor', () => {
  let revision: DefinitionRevision = 'released';

  function setUp(provideRevision = true): { http: HttpClient; controller: HttpTestingController } {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([definitionRevisionInterceptor])),
        provideHttpClientTesting(),
        ...(provideRevision ? [{ provide: DEFINITION_REVISION, useValue: () => revision }] : []),
      ],
    });
    return { http: TestBed.inject(HttpClient), controller: TestBed.inject(HttpTestingController) };
  }

  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('addresses a marked definition read to the current revision', () => {
    const { http, controller } = setUp();

    revision = 'released';
    http.get('/api/rules', { context: definitionRead() }).subscribe();
    controller.expectOne('/api/rules?revision=released').flush([]);

    revision = 'draft';
    http.get('/api/rules', { context: definitionRead() }).subscribe();
    controller.expectOne('/api/rules?revision=draft').flush([]);
  });

  it('leaves unmarked reads, writes and an explicit revision alone', () => {
    const { http, controller } = setUp();
    revision = 'released';

    http.get('/api/entities/order').subscribe();
    controller.expectOne('/api/entities/order').flush({});

    http.put('/api/rules/r1', {}, { context: definitionRead() }).subscribe();
    controller.expectOne((request) => request.method === 'PUT' && !request.params.has('revision')).flush({});

    http.get('/api/rules', { context: definitionRead(), params: { revision: 'draft' } }).subscribe();
    controller.expectOne('/api/rules?revision=draft').flush([]);
  });

  /** A host that has not wired the revision up keeps reading what it always read: the drafts. */
  it('reads drafts unless the host says otherwise', () => {
    const { http, controller } = setUp(false);

    http.get('/api/rules', { context: definitionRead() }).subscribe();
    controller.expectOne('/api/rules?revision=draft').flush([]);
  });
});
