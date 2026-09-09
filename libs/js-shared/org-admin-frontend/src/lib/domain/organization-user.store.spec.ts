import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ADA_DTO, GRACE_DTO, provideOrgAdminTesting, USERS_URL } from './test-organization-user';
import { OrganizationUserStore } from './organization-user.store';

describe('OrganizationUserStore', () => {
  let store: InstanceType<typeof OrganizationUserStore>;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: provideOrgAdminTesting() });
    store = TestBed.inject(OrganizationUserStore);
    controller = TestBed.inject(HttpTestingController);
    controller.expectOne(USERS_URL).flush([ADA_DTO, GRACE_DTO]);
  });

  it('lists the tenant’s users on init', () => {
    expect(store.entities()).toHaveLength(2);
    expect(store.entities().map((entity) => entity.username)).toEqual(['ada', 'grace']);
  });

  // The row is keyed by the identity provider's opaque id, which is what a details link carries.
  it('selects a user by the identity provider id', () => {
    store.setCurrentEntity('kc-2');

    expect(store.currentEntity()?.username).toBe('grace');
    // Disabled is not deleted: the account is still listed, which is the whole point of the flag.
    expect(store.currentEntity()?.enabled).toBe(false);
  });

  it('keeps the roles on the selected user, for the read-only column to join', () => {
    store.setCurrentEntity('kc-1');

    expect(store.currentEntity()?.roles).toEqual(['org-member', 'accountant']);
    expect(store.currentEntity()?.roleNames).toBe('org-member, accountant');
  });

  // What the container calls after an invitation: the identity provider decides what it stored, so
  // the list is re-read rather than having the returned row pushed into it.
  it('re-reads the list when asked to load again', () => {
    store.load({});

    controller.expectOne(USERS_URL).flush([ADA_DTO]);

    expect(store.entities()).toHaveLength(1);
  });
});
