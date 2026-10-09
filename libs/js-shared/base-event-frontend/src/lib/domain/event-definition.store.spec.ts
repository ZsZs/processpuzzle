import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { EventDefinitionStore } from './event-definition.store';
import { ORDER_CONFIRMED_EVENT_DTO, ORDER_CREATED_EVENT_DTO, pageOfEventDefinitions } from './test-event-definition';

describe('EventDefinitionStore', () => {
  const serviceRoot = 'http://localhost:8080/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof EventDefinitionStore>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { EVENT_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(EventDefinitionStore);
    TestBed.inject(HttpTestingController).expectOne(`${serviceRoot}/event-definitions`).flush(pageOfEventDefinitions(ORDER_CREATED_EVENT_DTO, ORDER_CONFIRMED_EVENT_DTO));
  });

  it('loads the catalog of the organization on init', () => {
    expect(store.entities().map((entity) => entity.id)).toEqual(['OrderCreatedEvent', 'OrderConfirmedEvent']);
  });

  it('selects a definition by the id other features store', () => {
    store.setCurrentEntity('OrderConfirmedEvent');

    expect(store.currentEntity()?.state).toBe('CONFIRMED');
  });
});
