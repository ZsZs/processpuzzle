import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { StateMachineDefinitionStore } from './state-machine-definition.store';
import { OTHER_STATE_MACHINE_DEFINITION_DTO, pageOfStateMachineDefinitions, STATE_MACHINE_DEFINITION_DTO } from './test-state-machine-definition';

describe('StateMachineDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof StateMachineDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { STATE_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(StateMachineDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/state-machines`).flush(pageOfStateMachineDefinitions(STATE_MACHINE_DEFINITION_DTO, OTHER_STATE_MACHINE_DEFINITION_DTO));
  });

  it('loads the machines of the organization on init', () => {
    expect(store.entities()).toHaveLength(2);
    expect(store.entities().map((entity) => entity.id)).toEqual(['order', 'dynamic-entity']);
  });

  // The list is keyed by the mirror of `entityName`, which is what a details link carries — so the
  // entity name is what has to select a row.
  it('selects a machine by the entity name it governs', () => {
    store.setCurrentEntity('order');

    expect(store.currentEntity()?.name).toBe('Order State Machine');
  });

  it('exposes the whole machine as current entity, not just its header fields', () => {
    store.setCurrentEntity('order');

    expect(store.currentEntity()?.states).toHaveLength(2);
    expect(store.currentEntity()?.transitions).toHaveLength(1);
    expect(store.currentEntity()?.transitions[0].actions[0].beanName).toBe('sendApprovalNotificationAction');
  });
});
