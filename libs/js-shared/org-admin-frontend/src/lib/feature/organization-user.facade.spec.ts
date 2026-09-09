import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { OrganizationUser } from '../domain/organization-user';
import { OrganizationUserMapper } from '../domain/organization-user.mapper';
import { OrganizationUserService } from '../domain/organization-user.service';
import { OrganizationUserStore } from '../domain/organization-user.store';
import { ORGANIZATION_USER_I18N_SCOPE } from '../org-admin.i18n';
import { provideOrgAdminTesting } from '../domain/test-organization-user';
import { OrganizationUserFacade } from './organization-user.facade';

describe('OrganizationUserFacade', () => {
  let facade: OrganizationUserFacade;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [...provideOrgAdminTesting(), OrganizationUserFacade] });
    facade = TestBed.inject(OrganizationUserFacade);
  });

  it('registers under the entity name the route and the transloco scope derive from', () => {
    expect(facade.entityType).toBe(OrganizationUser);
    expect(facade.entityName).toBe('Organization User');
    expect(facade.descriptor.i18nScope).toBe(ORGANIZATION_USER_I18N_SCOPE);
  });

  it('reuses the root-provided mapper, service and store', () => {
    expect(facade.mapper).toBe(TestBed.inject(OrganizationUserMapper));
    expect(facade.service).toBe(TestBed.inject(OrganizationUserService));
    expect(facade.storeClass).toBe(OrganizationUserStore);
    expect(facade.store).toBe(TestBed.inject(OrganizationUserStore));
  });

  it('binds the store to the descriptor it hands out', () => {
    expect(facade.descriptor.store).toBe(facade.store);
    expect(facade.attrDescriptors.length).toBeGreaterThan(0);
  });

  /**
   * The tab is *not* on the descriptor this facade builds - `OrganizationUserContainerComponent` adds
   * it, the way base-app and base-document do. The facade's descriptor is what the generic screens
   * resolve when the container is not in the tree, and a tab whose route only exists under this
   * library's own routes would render a dead link there.
   */
  it('leaves the role-assignment tab to the container that mounts it', () => {
    expect(facade.descriptor.extraTabs).toEqual([]);
  });

  it('names the username as the row title, so the status bar shows the person and not the type', () => {
    expect(facade.descriptor.titleKey).toBe('username');
    expect(facade.descriptor.entityTitle).toBeFalsy();
  });
});
