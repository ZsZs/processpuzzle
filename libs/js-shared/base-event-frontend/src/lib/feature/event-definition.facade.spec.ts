import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { EventDefinition } from '../domain/event-definition';
import { EventDefinitionMapper } from '../domain/event-definition.mapper';
import { EventDefinitionService } from '../domain/event-definition.service';
import { EventDefinitionStore } from '../domain/event-definition.store';
import { EventDefinitionFacade } from './event-definition.facade';

describe('EventDefinitionFacade', () => {
  let facade: EventDefinitionFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { EVENT_SERVICE_ROOT: 'http://localhost:8080/organizations/processpuzzle-testbed' } } },
        EventDefinitionFacade,
      ],
    });
    facade = TestBed.inject(EventDefinitionFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(EventDefinition);
    expect(facade.entityName).toBe('Event Definition');
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(EventDefinitionMapper));
    expect(facade.service).toBe(TestBed.inject(EventDefinitionService));
    expect(facade.storeClass).toBe(EventDefinitionStore);
    expect(facade.store).toBe(TestBed.inject(EventDefinitionStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
    expect(facade.attrDescriptors.length).toBeGreaterThan(0);
  });
});
