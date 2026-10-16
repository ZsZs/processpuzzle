import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BASE_ENTITY_FACADE_REGISTRY } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { BASE_EVENT_ENTITY_FACADES, BASE_EVENT_FACADE_PROVIDERS } from './base-event.providers';
import { EventDefinition, EventParameter } from './domain/event-definition';
import { EVENT_DEFINITION_ENTITY_NAME } from './domain/event-definition.descriptors';
import { EVENT_PARAMETER_ENTITY_NAME } from './domain/event-parameter.descriptors';

describe('BASE_EVENT facade providers', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { EVENT_SERVICE_ROOT: 'http://localhost:8080/organizations/processpuzzle-testbed' } } },
        ...BASE_EVENT_FACADE_PROVIDERS,
        { provide: BASE_ENTITY_FACADE_REGISTRY, useValue: BASE_EVENT_ENTITY_FACADES },
      ],
    });
  });

  // The parameters' EMBEDDED_COMPONENTS control resolves its child through the registry too.
  it('registers the catalog entry and the parameters embedded in it', () => {
    expect(Object.keys(BASE_EVENT_ENTITY_FACADES)).toEqual([EVENT_DEFINITION_ENTITY_NAME, EVENT_PARAMETER_ENTITY_NAME]);
    expect(BASE_EVENT_FACADE_PROVIDERS).toEqual(Object.values(BASE_EVENT_ENTITY_FACADES));
  });

  it('keys every facade by the entity name its own descriptor declares', () => {
    Object.entries(BASE_EVENT_ENTITY_FACADES).forEach(([entityName, facadeToken]) => {
      const facade = TestBed.inject(facadeToken);
      expect(facade.entityName).toBe(entityName);
      expect(facade.entityType).toBe(entityName === EVENT_PARAMETER_ENTITY_NAME ? EventParameter : EventDefinition);
      expect(facade.descriptor.store).toBeDefined();
    });
  });
});
