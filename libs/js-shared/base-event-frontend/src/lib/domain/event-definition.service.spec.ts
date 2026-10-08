import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { BaseEntityLoadResponse, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { EventDefinition } from './event-definition';
import { EventDefinitionMapper } from './event-definition.mapper';
import { EventDefinitionService } from './event-definition.service';
import { ORDER_CONFIRMED_EVENT_DTO, ORDER_CREATED_EVENT_DTO, pageOfEventDefinitions } from './test-event-definition';

describe('EventDefinitionService', () => {
  const serviceRoot = 'http://localhost:8080/organizations/processpuzzle-testbed';

  function configure(baseConfiguration: object) {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: baseConfiguration } }],
    });
    return { service: TestBed.inject(EventDefinitionService), controller: TestBed.inject(HttpTestingController) };
  }

  let service: EventDefinitionService;
  let controller: HttpTestingController;

  beforeEach(() => {
    ({ service, controller } = configure({ EVENT_SERVICE_ROOT: serviceRoot }));
  });

  it('lists the catalog of the configured organization', async () => {
    const pending = firstValueFrom(service.findAll());

    const request = controller.expectOne(`${serviceRoot}/event-definitions`);
    expect(request.request.method).toBe('GET');
    request.flush(pageOfEventDefinitions(ORDER_CREATED_EVENT_DTO, ORDER_CONFIRMED_EVENT_DTO));

    const result = (await pending) as BaseEntityLoadResponse<PersistedEntity<EventDefinition>>;
    expect(result.totalElements).toBe(2);
    expect(result.content.map((definition) => definition.id)).toEqual(['OrderCreatedEvent', 'OrderConfirmedEvent']);
    expect(result.content[1].state).toBe('CONFIRMED');
  });

  it('addresses a single definition by its author-chosen id', () => {
    service.delete('OrderCreatedEvent').subscribe();

    const request = controller.expectOne(`${serviceRoot}/event-definitions/OrderCreatedEvent`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });

  it('sends the mapped definition, version included, on update', () => {
    const entity = new EventDefinitionMapper().fromDto(ORDER_CONFIRMED_EVENT_DTO) as PersistedEntity<EventDefinition>;

    service.update(entity).subscribe();

    const request = controller.expectOne(`${serviceRoot}/event-definitions/OrderConfirmedEvent`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toMatchObject({ id: 'OrderConfirmedEvent', action: 'STATE_CHANGED', state: 'CONFIRMED', version: 2 });
    request.flush(ORDER_CONFIRMED_EVENT_DTO);
  });

  it('falls back to BACKEND_SERVICE_ROOT when no event root is configured', () => {
    const { service: fallbackService, controller: fallbackController } = configure({ BACKEND_SERVICE_ROOT: serviceRoot });

    fallbackService.delete('OrderCreatedEvent').subscribe();

    fallbackController.expectOne(`${serviceRoot}/event-definitions/OrderCreatedEvent`).flush(null);
  });
});
