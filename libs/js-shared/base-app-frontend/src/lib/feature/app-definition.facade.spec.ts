import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { AppDefinition } from '../domain/app-definition';
import { AppDefinitionMapper } from '../domain/app-definition.mapper';
import { AppDefinitionService } from '../domain/app-definition.service';
import { AppDefinitionStore } from '../domain/app-definition.store';
import { AppDefinitionFacade } from './app-definition.facade';

describe('AppDefinitionFacade', () => {
  let facade: AppDefinitionFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: 'http://localhost:3000/organizations/processpuzzle-testbed' } } },
        AppDefinitionFacade,
      ],
    });
    facade = TestBed.inject(AppDefinitionFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(AppDefinition);
    expect(facade.entityName).toBe('App Definition');
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(AppDefinitionMapper));
    expect(facade.service).toBe(TestBed.inject(AppDefinitionService));
    expect(facade.storeClass).toBe(AppDefinitionStore);
    expect(facade.store).toBe(TestBed.inject(AppDefinitionStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
    expect(facade.attrDescriptors.length).toBeGreaterThan(0);
  });
});
