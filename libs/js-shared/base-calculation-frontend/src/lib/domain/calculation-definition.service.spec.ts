import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { CalculationDefinitionService } from './calculation-definition.service';
import { ORDER_TOTAL_DTO, SERVICE_ROOT } from './test-calculation-definition';

describe('CalculationDefinitionService', () => {
  let service: CalculationDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: SERVICE_ROOT } } }],
    });
    service = TestBed.inject(CalculationDefinitionService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('lists the calculations of the configured organization, falling back to the backend root', async () => {
    const pending = firstValueFrom(service.findAll());

    controller.expectOne(`${SERVICE_ROOT}/calculation-definitions`).flush([ORDER_TOTAL_DTO]);

    expect(await pending).toBeTruthy();
  });

  it('addresses a definition by its id', () => {
    service.delete('order-total').subscribe();

    const request = controller.expectOne(`${SERVICE_ROOT}/calculation-definitions/order-total`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
