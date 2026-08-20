import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { StateMachineDefinition } from '../domain/state-machine-definition';
import { StateMachineDefinitionMapper } from '../domain/state-machine-definition.mapper';
import { StateMachineDefinitionService } from '../domain/state-machine-definition.service';
import { StateMachineDefinitionStore } from '../domain/state-machine-definition.store';
import { StateMachineDefinitionFacade } from './state-machine-definition.facade';

describe('StateMachineDefinitionFacade', () => {
  let facade: StateMachineDefinitionFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { STATE_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        StateMachineDefinitionFacade,
      ],
    });
    facade = TestBed.inject(StateMachineDefinitionFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(StateMachineDefinition);
    expect(facade.entityName).toBe('State Machine Definition');
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(StateMachineDefinitionMapper));
    expect(facade.service).toBe(TestBed.inject(StateMachineDefinitionService));
    expect(facade.storeClass).toBe(StateMachineDefinitionStore);
    expect(facade.store).toBe(TestBed.inject(StateMachineDefinitionStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
    expect(facade.attrDescriptors.length).toBeGreaterThan(0);
  });
});
