import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { beforeEach, describe, expect, it } from 'vitest';
import { RoleDefinitionStore } from './role-definition.store';
import { OTHER_ROLE_DEFINITION_DTO, ROLE_DEFINITION_DTO } from './test-role-definition';

describe('RoleDefinitionStore', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let store: InstanceType<typeof RoleDefinitionStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { WORKFLOW_SERVICE_ROOT: serviceRoot } } }],
    });
    store = TestBed.inject(RoleDefinitionStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(`${serviceRoot}/roles`).flush([ROLE_DEFINITION_DTO, OTHER_ROLE_DEFINITION_DTO]);
  });

  it('loads the roles of the organization on init', () => {
    expect(store.entities().map((entity) => entity.id)).toEqual(['clerk', 'manager']);
  });

  // This store is what a workflow's `roles` control and an assignment's `performedBy` picker read from,
  // so a role has to be reachable by the id those attributes hold.
  it('exposes a role by the id the referencing attributes name', () => {
    store.setCurrentEntity('clerk');

    expect(store.currentEntity()?.name).toBe('Order Clerk');
    expect(store.currentEntity()?.entityRoleId).toBe('clerk-role');
  });
});
