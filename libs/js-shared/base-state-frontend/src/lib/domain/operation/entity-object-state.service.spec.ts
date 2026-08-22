import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { EntityObjectStateService } from './entity-object-state.service';

describe('EntityObjectStateService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  const objectId = '46ecc74f-6bc2-4282-9a4f-58ab0e259c28';
  const stateUrl = `${serviceRoot}/entities/order/${objectId}/state`;

  let service: EntityObjectStateService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { STATE_SERVICE_ROOT: serviceRoot } } }],
    });
    service = TestBed.inject(EntityObjectStateService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('reads the state of one object from the operation resource', async () => {
    const pending = firstValueFrom(service.findState('order', objectId));

    const request = controller.expectOne(stateUrl);
    expect(request.request.method).toBe('GET');
    request.flush({
      objectId,
      entityName: 'order',
      currentStateKey: 'CONFIRMED',
      isFinal: false,
      availableTransitions: [{ transitionKey: 'ship', triggerKey: 'ship', targetStateKey: 'SHIPPED', guardsSatisfied: true }],
    });

    const state = await pending;
    expect(state?.currentStateKey).toBe('CONFIRMED');
    expect(state?.isFinal).toBe(false);
    expect(state?.availableTransitions.map((transition) => transition.triggerKey)).toEqual(['ship']);
  });

  /** No machine governs the type, or no object has that id. Neither is worth an error on a read-only tab. */
  it('answers undefined on 404 rather than failing', async () => {
    const pending = firstValueFrom(service.findState('order', objectId));

    controller.expectOne(stateUrl).flush('no state machine', { status: 404, statusText: 'Not Found' });

    expect(await pending).toBeUndefined();
  });

  it('propagates any other failure', async () => {
    const pending = firstValueFrom(service.findState('order', objectId));

    controller.expectOne(stateUrl).flush('boom', { status: 500, statusText: 'Server Error' });

    await expect(pending).rejects.toBeDefined();
  });

  /** json-server and the Firebase functions are not generated from the contract; only Spring is. */
  it('defaults the optional fields of a partial response and reports a stateless one as absent', async () => {
    const partial = firstValueFrom(service.findState('order', objectId));
    controller.expectOne(stateUrl).flush({ currentStateKey: 'DRAFT' });
    expect(await partial).toEqual({ objectId: '', entityName: '', currentStateKey: 'DRAFT', isFinal: false, enteredStateAt: undefined, availableTransitions: [] });

    const stateless = firstValueFrom(service.findState('order', objectId));
    controller.expectOne(stateUrl).flush({ objectId, entityName: 'order' });
    expect(await stateless).toBeUndefined();
  });

  it('percent-encodes the ids it puts in the path', async () => {
    const pending = firstValueFrom(service.findState('special order', 'a/b'));

    const request = controller.expectOne(`${serviceRoot}/entities/special%20order/a%2Fb/state`);
    request.flush({ currentStateKey: 'DRAFT' });

    await pending;
  });
});
