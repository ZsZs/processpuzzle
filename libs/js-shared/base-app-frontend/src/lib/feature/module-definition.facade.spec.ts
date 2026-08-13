import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ModuleDefinition } from '../domain/module-definition';
import { ModuleDefinitionMapper } from '../domain/module-definition.mapper';
import { ModuleDefinitionService } from '../domain/module-definition.service';
import { ModuleDefinitionStore } from '../domain/module-definition.store';
import { ModuleDefinitionFacade } from './module-definition.facade';

describe('ModuleDefinitionFacade', () => {
  let facade: ModuleDefinitionFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        ModuleDefinitionFacade,
      ],
    });
    facade = TestBed.inject(ModuleDefinitionFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(ModuleDefinition);
    expect(facade.entityName).toBe('Module Definition');
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(ModuleDefinitionMapper));
    expect(facade.service).toBe(TestBed.inject(ModuleDefinitionService));
    expect(facade.storeClass).toBe(ModuleDefinitionStore);
    expect(facade.store).toBe(TestBed.inject(ModuleDefinitionStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
    expect(facade.attrDescriptors.length).toBeGreaterThan(0);
  });
});
