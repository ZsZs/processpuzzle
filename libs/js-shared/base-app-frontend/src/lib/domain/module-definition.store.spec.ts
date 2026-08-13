import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { ModuleDefinitionStore } from './module-definition.store';
import { MODULE_DEFINITION_DTO, OTHER_MODULE_DEFINITION_DTO, pageOfModuleDefinitions } from './test-module-definition';

describe('ModuleDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof ModuleDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APP_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(ModuleDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    // The store loads its list on init. The payload is the page of complete modules the backend answers
    // with — the form reads `currentEntity` out of this list, so anything the list drops is lost on save.
    controller.expectOne(`${serviceRoot}/modules`).flush(pageOfModuleDefinitions(MODULE_DEFINITION_DTO, OTHER_MODULE_DEFINITION_DTO));
  });

  it('keys its entities by the module key the contract calls key', () => {
    expect(store.entities().map((module) => module.id)).toEqual(['order-admin', 'claims']);
  });

  it('exposes the whole module graph as current entity, not just its header fields', () => {
    store.setCurrentEntity('order-admin');

    expect(store.currentEntity()?.name).toBe('Order administration');
    expect(store.currentEntity()?.translocoScope).toBe('order_admin');
    expect(store.currentEntity()?.routes?.map((route) => route.path)).toEqual(['lines', 'line/:id']);
    expect(store.currentEntity()?.routes?.[0].entityMode).toBe('LIST');
  });

  /**
   * Plain generic CRUD, deliberately: publishing is an `AppDefinition` operation, and a module with a
   * lifecycle of its own would make "which version of what is live" a question with two answers.
   */
  it('has no publish operation of its own', () => {
    expect('publish' in store).toBe(false);
  });
});
