import { BaseEntity } from '@processpuzzle/base-entity';

/**
 * A user of one organization, as the tenant's Keycloak realm holds it.
 *
 * **There is no user table anywhere in ProcessPuzzle.** Keycloak is the system of record, and
 * `org-admin-backend` proxies its Admin API — which has three consequences a caller has to know:
 *
 * - `id` is the identity provider's own opaque id. Not a ProcessPuzzle id, not the username, and not
 *   safe to parse or to assume survives a realm re-import.
 * - a role granted here lands in the user's token on **next login**, not immediately. The API answers
 *   with the new role list at once, but an already-issued token keeps the old one until refreshed.
 * - `enabled: false` is the reversible way to remove someone. It keeps the account, its groups and
 *   its roles while making a token unobtainable, so the platform's `createdBy` and `assignee`
 *   references keep resolving. Deletion is irreversible and leaves those dangling.
 */
export class OrganizationUser implements BaseEntity {
  readonly id: string;
  readonly username: string;
  email: string | undefined;
  firstName: string | undefined;
  lastName: string | undefined;
  enabled: boolean;
  emailVerified: boolean;
  createdAt: string | undefined;
  roles: string[];

  constructor(
    id?: string,
    username?: string,
    email?: string,
    firstName?: string,
    lastName?: string,
    enabled?: boolean,
    emailVerified?: boolean,
    createdAt?: string,
    roles?: string[],
  ) {
    this.id = id ?? '';
    this.username = username ?? '';
    this.email = email;
    this.firstName = firstName;
    this.lastName = lastName;
    this.enabled = enabled ?? true;
    this.emailVerified = emailVerified ?? false;
    this.createdAt = createdAt;
    this.roles = roles ?? [];
  }

  /** First and last name joined, falling back to the username — what a list column should show. */
  get displayName(): string {
    const full = [this.firstName, this.lastName].filter((part) => !!part && part.length > 0).join(' ');
    return full.length > 0 ? full : this.username;
  }

  /** The roles as one string, for the read-only column in the generated table. */
  get roleNames(): string {
    return this.roles.join(', ');
  }
}

/**
 * Whom to invite into the organization - the create payload, which is deliberately not what
 * {@link OrganizationUserMapper.toDto} produces.
 *
 * The two shapes differ in both directions, which is why the invitation has an interface of its own:
 * `username` and `roles` are required here and absent from the update payload, while `enabled` is the
 * other way round. There is no password field on purpose - the backend creates the account without
 * credentials and with a required reset, so the invitee sets their own on first sign-in and the
 * administrator never learns it.
 */
export interface OrganizationUserInvitation {
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  /** Empty means `org-member` alone; the backend adds it to whatever is chosen either way. */
  roles: string[];
}

/**
 * A realm role the tenant declares.
 *
 * `platformManaged` is true for `org-admin` and `org-member`, the two created with every realm and
 * the only two ProcessPuzzle itself interprets. Everything else is the tenant's own, and is what
 * `NavNode.roles` and a workflow's role definitions are matched against — which is why the role list
 * is read live from the realm rather than being a fixed enum.
 */
export class OrganizationRole implements BaseEntity {
  readonly id: string;
  readonly name: string;
  description: string | undefined;
  platformManaged: boolean;

  constructor(name?: string, description?: string, platformManaged?: boolean) {
    this.name = name ?? '';
    this.id = this.name;
    this.description = description;
    this.platformManaged = platformManaged ?? false;
  }
}
