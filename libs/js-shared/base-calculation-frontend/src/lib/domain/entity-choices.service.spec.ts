import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { afterEach, describe, expect, it } from 'vitest';
import { ENTITY_CHOICES_LIMIT, EntityChoicesService } from './entity-choices.service';

const ROOT = 'http://localhost:8080/organizations/test-org';

function setUp(configuration: unknown = { BASE_CONFIGURATION: { ENTITY_SERVICE_ROOT: ROOT } }) {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: configuration }],
  });
  return { service: TestBed.inject(EntityChoicesService), controller: TestBed.inject(HttpTestingController) };
}

describe('EntityChoicesService', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('labels each object by the attribute that titles it, and by its id when that is empty', async () => {
    const { service, controller } = setUp();
    const pending = firstValueFrom(service.choicesOf('order line'));

    const objects = controller.expectOne((request) => request.url === `${ROOT}/entities/order%20line`);
    expect(objects.request.params.get('size')).toBe(String(ENTITY_CHOICES_LIMIT));
    objects.flush({ content: [{ id: 'o-1', payload: { orderNumber: 'ORD-1' } }, { id: 'o-2', payload: {} }], totalElements: 2 });
    controller.expectOne(`${ROOT}/entity-definitions/order%20line`).flush({ attributes: [{ code: 'customerName' }, { code: 'orderNumber', isLinkToDetails: true }] });

    expect(await pending).toEqual([
      { id: 'o-1', label: 'ORD-1' },
      { id: 'o-2', label: 'o-2' },
    ]);
  });

  it('still offers the objects, by id, when the definition cannot be read', async () => {
    const { service, controller } = setUp();
    const pending = firstValueFrom(service.choicesOf('order'));

    controller.expectOne((request) => request.url === `${ROOT}/entities/order`).flush({ content: [{ id: 'o-1', payload: { orderNumber: 'ORD-1' } }], totalElements: 1 });
    controller.expectOne(`${ROOT}/entity-definitions/order`).flush('gone', { status: 404, statusText: 'Not Found' });

    expect(await pending).toEqual([{ id: 'o-1', label: 'o-1' }]);
  });

  it('offers nothing for a type with more objects than it lists, or when the objects cannot be read', async () => {
    const { service, controller } = setUp();
    const tooMany = firstValueFrom(service.choicesOf('order'));
    controller.expectOne((request) => request.url === `${ROOT}/entities/order`).flush({ content: [{ id: 'o-1' }], totalElements: ENTITY_CHOICES_LIMIT + 1 });
    controller.expectOne(`${ROOT}/entity-definitions/order`).flush({ attributes: [] });
    expect(await tooMany).toEqual([]);

    const failing = firstValueFrom(service.choicesOf('order'));
    controller.expectOne(`${ROOT}/entity-definitions/order`).flush({ attributes: [] });
    controller.expectOne((request) => request.url === `${ROOT}/entities/order`).flush('down', { status: 503, statusText: 'Unavailable' });
    expect(await failing).toEqual([]);
  });

  it('asks nothing without a service root', async () => {
    const { service } = setUp({ BASE_CONFIGURATION: {} });
    expect(await firstValueFrom(service.choicesOf('order'))).toEqual([]);
  });
});
