import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { OrganizationRole } from '../domain/organization-user';
import { OrganizationRoleMapper } from '../domain/organization-user.mapper';
import { OrganizationRoleEntityService } from '../domain/organization-user.service';
import { OrganizationRoleStore } from '../domain/organization-role.store';
import { ORGANIZATION_ROLE_ENTITY_NAME } from '../domain/organization-role.descriptors';
import { provideOrgAdminTesting } from '../domain/test-organization-user';
import { OrganizationRoleFacade } from './organization-role.facade';

describe('OrganizationRoleFacade', () => {
  let facade: OrganizationRoleFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideOrgAdminTesting(), OrganizationRoleFacade] });
    facade = TestBed.inject(OrganizationRoleFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(OrganizationRole);
    expect(facade.entityName).toBe(ORGANIZATION_ROLE_ENTITY_NAME);
    expect(facade.descriptor.entityName).toBe(ORGANIZATION_ROLE_ENTITY_NAME);
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(OrganizationRoleMapper));
    expect(facade.service).toBe(TestBed.inject(OrganizationRoleEntityService));
    expect(facade.storeClass).toBe(OrganizationRoleStore);
    expect(facade.store).toBe(TestBed.inject(OrganizationRoleStore));
  });
});
