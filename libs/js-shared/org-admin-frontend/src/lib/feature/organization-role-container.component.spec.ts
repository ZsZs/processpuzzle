import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { provideOrgAdminTesting } from '../domain/test-organization-user';
import { OrganizationRoleStore } from '../domain/organization-role.store';
import { ORGANIZATION_ROLE_ENTITY_NAME } from '../domain/organization-role.descriptors';
import { OrganizationRoleContainerComponent } from './organization-role-container.component';

describe('OrganizationRoleContainerComponent', () => {
  it('hands the role descriptor the role store it lists and edits through', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [...provideOrgAdminTesting()] });

    const component = TestBed.runInInjectionContext(() => new OrganizationRoleContainerComponent());

    expect(component.baseEntityDescriptor.entityName).toBe(ORGANIZATION_ROLE_ENTITY_NAME);
    expect(component.baseEntityDescriptor.store).toBe(TestBed.inject(OrganizationRoleStore));
  });
});
