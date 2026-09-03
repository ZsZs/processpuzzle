import { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { expect } from 'vitest';
import { ORG_ADMIN_ORG_KEY } from './organization-user.service';

/** The tenant every spec in this library administers. */
export const ORG_KEY = 'my-org';
export const ORG_ADMIN_SERVICE_ROOT = 'http://localhost:8080/api';
export const ADMIN_URL = `${ORG_ADMIN_SERVICE_ROOT}/organizations/${ORG_KEY}/admin`;
export const USERS_URL = `${ADMIN_URL}/users`;

export const ADA_DTO = { id: 'kc-1', username: 'ada', email: 'ada@my-org.example', firstName: 'Ada', lastName: 'Lovelace', enabled: true, emailVerified: true, roles: ['org-member', 'accountant'] };
export const GRACE_DTO = { id: 'kc-2', username: 'grace', email: 'grace@my-org.example', enabled: false, emailVerified: false, roles: ['org-member'] };

export const REALM_ROLE_DTOS = [
  { name: 'org-admin', description: 'Administers the organization.', platformManaged: true },
  { name: 'org-member', description: 'Every member holds it.', platformManaged: true },
  { name: 'accountant', platformManaged: false },
];

/**
 * The providers every org-admin spec needs.
 *
 * `ORG_ADMIN_ORG_KEY` is among them because the services read it inside their `super(...)` call — a
 * spec that leaves it out fails at injection rather than at the assertion.
 */
export function provideOrgAdminTesting(orgKey: string = ORG_KEY): (Provider | EnvironmentProviders)[] {
  return [
    provideHttpClient(),
    provideHttpClientTesting(),
    { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { ORG_ADMIN_SERVICE_ROOT } } },
    { provide: ORG_ADMIN_ORG_KEY, useValue: orgKey },
  ];
}

/**
 * `BaseEntityStore` lists on init, so injecting anything that reaches the store issues a collection
 * GET before the spec's first assertion. Left unflushed it either times out the next `expectOne` or
 * leaks into it — see [[base-entity-store-lists-on-init]].
 */
export function flushUserList(controller: HttpTestingController, ...dtos: unknown[]): void {
  controller.expectOne(USERS_URL).flush(dtos.length > 0 ? dtos : [ADA_DTO, GRACE_DTO]);
}

/** Fails with the selector rather than with `null.click is not a function`. */
export function required<T extends Element>(host: HTMLElement, selector: string): T {
  const element = host.querySelector<T>(selector);
  expect(element, `expected ${selector} to be rendered`).not.toBeNull();
  return element as T;
}
