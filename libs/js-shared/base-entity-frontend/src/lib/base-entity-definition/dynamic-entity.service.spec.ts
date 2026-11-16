import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { DynamicEntityService } from './dynamic-entity.service';

describe('DynamicEntityService', () => {
  let service: DynamicEntityService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: 'http://localhost:8080/organizations/acme' } } },
      ],
    });
    service = TestBed.runInInjectionContext(() => new DynamicEntityService('asset'));
    controller = TestBed.inject(HttpTestingController);
  });

  it('reinstates a retired record by posting to its reinstate resource', () => {
    let answered = false;
    service.reinstate('b7e1').subscribe(() => (answered = true));

    const request = controller.expectOne('http://localhost:8080/organizations/acme/entities/asset/b7e1/reinstate');
    expect(request.request.method).toBe('POST');
    request.flush({ id: 'b7e1' });
    expect(answered).toBe(true);
  });
});
