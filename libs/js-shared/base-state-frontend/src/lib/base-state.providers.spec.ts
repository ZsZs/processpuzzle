import { Injector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { BASE_ENTITY_FACADE_REGISTRY, BaseEntityDescriptor } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BASE_STATE_ENTITY_FACADES, BASE_STATE_FACADE_PROVIDERS } from './base-state.providers';
import { BeanRef, State, StateMachineDefinition, Transition } from './domain/state-machine-definition';
import {
  STATE_MACHINE_DEFINITION_ENTITY_NAME,
  STATE_MACHINE_STATE_ENTITY_NAME,
  STATE_MACHINE_TRANSITION_ENTITY_NAME,
  STATE_TRANSITION_ACTION_ENTITY_NAME,
  STATE_TRANSITION_GUARD_ENTITY_NAME,
} from './domain/state-entity-names';

describe('BASE_STATE facade providers', () => {
  let injector: Injector;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { STATE_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        ...BASE_STATE_FACADE_PROVIDERS,
        // The embedded facades reach each other's stores and descriptors through the registry, which is
        // exactly what the application wires up by spreading the same map.
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: BASE_STATE_ENTITY_FACADES },
      ],
    });
    injector = TestBed.inject(Injector);
  });

  const descriptorOf = (entityName: string): BaseEntityDescriptor => injector.get(BASE_STATE_ENTITY_FACADES[entityName]).descriptor;

  it('registers the routable definition and the four embedded levels below it', () => {
    expect(Object.keys(BASE_STATE_ENTITY_FACADES)).toEqual([
      STATE_MACHINE_DEFINITION_ENTITY_NAME,
      STATE_MACHINE_STATE_ENTITY_NAME,
      STATE_MACHINE_TRANSITION_ENTITY_NAME,
      STATE_TRANSITION_GUARD_ENTITY_NAME,
      STATE_TRANSITION_ACTION_ENTITY_NAME,
    ]);
    expect(BASE_STATE_FACADE_PROVIDERS).toHaveLength(Object.keys(BASE_STATE_ENTITY_FACADES).length);
  });

  it('keys every facade by the entity name its own descriptor declares', () => {
    Object.entries(BASE_STATE_ENTITY_FACADES).forEach(([entityName, facadeToken]) => {
      expect(injector.get(facadeToken).entityName).toBe(entityName);
    });
  });

  it('gives each level the entity type its blank row is minted from', () => {
    expect(injector.get(BASE_STATE_ENTITY_FACADES[STATE_MACHINE_DEFINITION_ENTITY_NAME]).entityType).toBe(StateMachineDefinition);
    expect(injector.get(BASE_STATE_ENTITY_FACADES[STATE_MACHINE_STATE_ENTITY_NAME]).entityType).toBe(State);
    expect(injector.get(BASE_STATE_ENTITY_FACADES[STATE_MACHINE_TRANSITION_ENTITY_NAME]).entityType).toBe(Transition);
    // A guard and an action share their type — the contract's two schemas are the same two fields — and
    // are told apart by their descriptor, which is why they still need a facade each.
    expect(injector.get(BASE_STATE_ENTITY_FACADES[STATE_TRANSITION_GUARD_ENTITY_NAME]).entityType).toBe(BeanRef);
    expect(injector.get(BASE_STATE_ENTITY_FACADES[STATE_TRANSITION_ACTION_ENTITY_NAME]).entityType).toBe(BeanRef);
  });

  // One root: everything else travels inside the definition's document, the contract giving neither a
  // state nor a transition an endpoint of its own.
  it('makes the definition the only aggregate root of the graph', () => {
    expect(descriptorOf(STATE_MACHINE_DEFINITION_ENTITY_NAME).isEmbedded).toBe(false);
    [STATE_MACHINE_STATE_ENTITY_NAME, STATE_MACHINE_TRANSITION_ENTITY_NAME, STATE_TRANSITION_GUARD_ENTITY_NAME, STATE_TRANSITION_ACTION_ENTITY_NAME].forEach((entityName) => {
      expect(descriptorOf(entityName).isEmbedded).toBe(true);
    });
  });

  it('gives the two bean reference levels a store each, so their rows cannot mix', () => {
    const guardFacade = injector.get(BASE_STATE_ENTITY_FACADES[STATE_TRANSITION_GUARD_ENTITY_NAME]);
    const actionFacade = injector.get(BASE_STATE_ENTITY_FACADES[STATE_TRANSITION_ACTION_ENTITY_NAME]);

    expect(guardFacade.storeClass).not.toBe(actionFacade.storeClass);
  });

  it('binds a store to every descriptor, which is what an embedded list reads its rows from', () => {
    Object.keys(BASE_STATE_ENTITY_FACADES).forEach((entityName) => expect(descriptorOf(entityName).store).toBeDefined());
  });

  /**
   * The invariant the runtime enforces by throwing on first render: `EmbeddedComponentsListComponent`
   * resolves its rows through the registry, so a child named by an `EMBEDDED_COMPONENTS` attribute but
   * missing from the map is a form that renders and then fails.
   */
  it('registers every entity an embedded attribute of the graph names', () => {
    Object.keys(BASE_STATE_ENTITY_FACADES).forEach((entityName) => {
      descriptorOf(entityName)
        .embeddedAttrDescriptors()
        .forEach((attrDescriptor) => {
          expect(BASE_STATE_ENTITY_FACADES[attrDescriptor.linkedEntityType as string], `'${entityName}.${attrDescriptor.attrName}' -> '${attrDescriptor.linkedEntityType}'`).toBeDefined();
        });
    });
  });

  /** The mirror of the check above: a child has to name the entity that carries it, or the control throws. */
  it('lets every embedded child name the owner that carries it', () => {
    Object.keys(BASE_STATE_ENTITY_FACADES).forEach((entityName) => {
      descriptorOf(entityName)
        .embeddedAttrDescriptors()
        .forEach((attrDescriptor) => {
          expect(descriptorOf(attrDescriptor.linkedEntityType as string).isComponentOf(entityName)).toBe(true);
        });
    });
  });
});
