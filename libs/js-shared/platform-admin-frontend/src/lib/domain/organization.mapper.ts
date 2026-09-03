import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { Organization, OrganizationProperties, OrganizationStatus } from './organization';

type OrganizationDto = Partial<Organization>;

@Injectable({ providedIn: 'root' })
export class OrganizationMapper implements BaseEntityMapper<Organization> {
  fromDto(dto: unknown): Organization {
    const source = dto as OrganizationDto;
    const properties: OrganizationProperties = {
      key: source.key,
      name: source.name,
      description: source.description,
      contactEmail: source.contactEmail,
      defaultLocale: source.defaultLocale,
      status: source.status as OrganizationStatus | undefined,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    };
    return new Organization(properties);
  }

  /**
   * Sends only what `OrganizationUpdate` declares.
   *
   * A spread would carry `key`, `id`, `status` and the two timestamps, and the backend rejects
   * unknown fields on a validated payload — but the more important reason is `status`: it is settable
   * only through the suspend and activate operations, because each has a Keycloak call to make
   * alongside the write. A form that could PUT `status: ACTIVE` would hand out a tenant whose realm
   * was never created.
   */
  toDto(entity: Organization): unknown {
    return {
      name: entity.name,
      description: entity.description,
      contactEmail: entity.contactEmail,
      defaultLocale: entity.defaultLocale,
    };
  }
}
