import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { OrganizationRole, OrganizationUser } from './organization-user';

type OrganizationUserDto = Partial<OrganizationUser>;
type OrganizationRoleDto = Partial<OrganizationRole>;

@Injectable({ providedIn: 'root' })
export class OrganizationUserMapper implements BaseEntityMapper<OrganizationUser> {
  fromDto(dto: unknown): OrganizationUser {
    const source = dto as OrganizationUserDto;
    return new OrganizationUser(source);
  }

  /**
   * Sends only what `OrganizationUserUpdate` declares.
   *
   * `username` is absent because it is what audit records name, so the platform treats it as
   * immutable even though the identity provider would allow a rename. `roles` is absent because
   * assigning them is a separate authorization decision with its own endpoint — folding it into a
   * profile edit would mean every name correction silently rewrote someone's permissions.
   */
  toDto(entity: OrganizationUser): unknown {
    return {
      email: entity.email,
      firstName: entity.firstName,
      lastName: entity.lastName,
      enabled: entity.enabled,
    };
  }
}

@Injectable({ providedIn: 'root' })
export class OrganizationRoleMapper implements BaseEntityMapper<OrganizationRole> {
  fromDto(dto: unknown): OrganizationRole {
    const source = dto as OrganizationRoleDto;
    return new OrganizationRole(source.name, source.description, source.platformManaged);
  }

  /**
   * Roles are read-only through this library: the API has no create or update for them, because a
   * role minted from a typo is one nothing in the platform ever matches. The method exists only
   * because `BaseEntityMapper` requires it.
   */
  toDto(entity: OrganizationRole): unknown {
    return { ...entity };
  }
}
