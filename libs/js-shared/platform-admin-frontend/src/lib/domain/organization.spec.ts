import { describe, expect, it } from 'vitest';
import { Organization, OrganizationStatus } from './organization';
import { OrganizationMapper } from './organization.mapper';

describe('Organization', () => {
  it('uses the tenant key as its BaseEntity id', () => {
    // Not decoration: every /platform/organizations/{orgKey} call is keyed by this value, so a
    // separate generated id would make the list and the form address rows the API does not know.
    const organization = new Organization({ key: 'my-org', name: 'My Organization Ltd.' });

    expect(organization.id).toBe('my-org');
    expect(organization.key).toBe('my-org');
  });

  it('defaults to PROVISIONING, which is what a freshly created tenant really is', () => {
    expect(new Organization({ key: 'my-org', name: 'My Org' }).status).toBe(OrganizationStatus.PROVISIONING);
  });

  it('reports its lifecycle state', () => {
    const provisioning = new Organization({ key: 'a', name: 'A', status: OrganizationStatus.PROVISIONING });
    const active = new Organization({ key: 'b', name: 'B', status: OrganizationStatus.ACTIVE });
    const suspended = new Organization({ key: 'c', name: 'C', status: OrganizationStatus.SUSPENDED });

    expect([provisioning.isProvisioning, provisioning.isActive, provisioning.isSuspended]).toEqual([true, false, false]);
    expect([active.isProvisioning, active.isActive, active.isSuspended]).toEqual([false, true, false]);
    expect([suspended.isProvisioning, suspended.isActive, suspended.isSuspended]).toEqual([false, false, true]);
  });
});

describe('OrganizationMapper', () => {
  const mapper = new OrganizationMapper();

  it('reads every field the contract declares', () => {
    const organization = mapper.fromDto({
      key: 'my-org',
      name: 'My Organization Ltd.',
      description: 'Insurance.',
      contactEmail: 'ops@my-org.example',
      defaultLocale: 'en-GB',
      status: 'SUSPENDED',
      createdAt: '2026-08-01T00:00:00Z',
      updatedAt: '2026-09-01T00:00:00Z',
    });

    expect(organization.key).toBe('my-org');
    expect(organization.name).toBe('My Organization Ltd.');
    expect(organization.description).toBe('Insurance.');
    expect(organization.contactEmail).toBe('ops@my-org.example');
    expect(organization.defaultLocale).toBe('en-GB');
    expect(organization.status).toBe(OrganizationStatus.SUSPENDED);
    expect(organization.createdAt).toBe('2026-08-01T00:00:00Z');
  });

  // The load-bearing assertion in this file. `status` is settable only through the suspend and
  // activate operations, each of which has a Keycloak call to make alongside the write — a PUT
  // carrying `status: ACTIVE` would hand out a tenant whose realm was never created. The other four
  // dropped fields are simply not in OrganizationUpdate.
  it('sends only the four mutable fields, never the key, the status or the timestamps', () => {
    const dto = mapper.toDto(
      new Organization({
        key: 'my-org',
        name: 'My Org',
        description: 'Insurance.',
        contactEmail: 'ops@my-org.example',
        defaultLocale: 'en-GB',
        status: OrganizationStatus.ACTIVE,
        createdAt: '2026-08-01T00:00:00Z',
        updatedAt: '2026-09-01T00:00:00Z',
      }),
    );

    expect(dto).toEqual({
      name: 'My Org',
      description: 'Insurance.',
      contactEmail: 'ops@my-org.example',
      defaultLocale: 'en-GB',
    });
    expect(Object.keys(dto as object)).not.toContain('status');
    expect(Object.keys(dto as object)).not.toContain('key');
    expect(Object.keys(dto as object)).not.toContain('id');
  });

  it('tolerates a partial payload rather than producing undefined fields', () => {
    const organization = mapper.fromDto({ key: 'my-org' });

    expect(organization.name).toBe('');
    expect(organization.status).toBe(OrganizationStatus.PROVISIONING);
  });
});
