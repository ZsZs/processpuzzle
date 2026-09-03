import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * Lifecycle of a tenant, mirroring `platformadmin.domain.OrganizationStatus`.
 *
 * `PROVISIONING` is observable, not transient: the backend commits the row before creating the
 * tenant's Keycloak realm, so a freshly created organization is genuinely in this state for as long
 * as that call takes — and stays in it if the call fails. A list that treated it as a flicker would
 * hide exactly the failure an operator needs to see.
 */
export enum OrganizationStatus {
  PROVISIONING = 'PROVISIONING',
  ACTIVE = 'ACTIVE',
  SUSPENDED = 'SUSPENDED',
}

export interface OrganizationProperties {
  key?: string;
  name?: string;
  description?: string;
  contactEmail?: string;
  defaultLocale?: string;
  status?: OrganizationStatus;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * A tenant.
 *
 * `key` is `readonly` because it is the public URL segment of the tenant's application and the scope
 * of every piece of its metadata — renaming it would orphan every id, which is why the contract's
 * update payload has no `key` field at all.
 *
 * `id` is the key. `BaseEntity` requires an `id`, and minting a separate one would mean the list and
 * the form addressed rows by something the API does not know: every `/platform/organizations/{orgKey}`
 * call is keyed by this value.
 */
export class Organization implements BaseEntity {
  readonly id: string;
  readonly key: string;
  name: string;
  description: string | undefined;
  contactEmail: string | undefined;
  defaultLocale: string | undefined;
  status: OrganizationStatus;
  createdAt: string | undefined;
  updatedAt: string | undefined;

  constructor(properties: OrganizationProperties = {}) {
    this.key = properties.key ?? '';
    this.id = this.key;
    this.name = properties.name ?? '';
    this.description = properties.description;
    this.contactEmail = properties.contactEmail;
    this.defaultLocale = properties.defaultLocale;
    this.status = properties.status ?? OrganizationStatus.PROVISIONING;
    this.createdAt = properties.createdAt;
    this.updatedAt = properties.updatedAt;
  }

  /** Whether the tenant is usable — the only state in which its members can obtain a token. */
  get isActive(): boolean {
    return this.status === OrganizationStatus.ACTIVE;
  }

  /** Whether the tenant's realm is still being created, or failed to be. */
  get isProvisioning(): boolean {
    return this.status === OrganizationStatus.PROVISIONING;
  }

  get isSuspended(): boolean {
    return this.status === OrganizationStatus.SUSPENDED;
  }
}
