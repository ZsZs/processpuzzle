import { BaseEntity } from '@processpuzzle/base-entity';
import { v4 as uuidv4 } from 'uuid';

export class User implements BaseEntity {
  readonly id: string;
  private _email: string | null | undefined;
  private _password: string | null | undefined;
  private _firstName: string | undefined;
  private _lastName: string | undefined;
  private _photoUrl: string | undefined;
  private _roles: readonly string[] = [];

  constructor(email?: string | null, id?: string, firstName?: string | null, lastName?: string | null, photoUrl?: string | null, roles?: readonly string[] | null) {
    this._email = email;
    this.id = id ?? uuidv4();
    this.firstName = firstName ?? '';
    this.lastName = lastName ?? '';
    this.photoUrl = photoUrl ?? '';
    this.roles = roles ?? [];
  }

  // region properties
  public get email(): string | null | undefined {
    return this._email;
  }

  public set email(email: string) {
    this._email = email;
  }

  public get firstName(): string | undefined {
    return this._firstName;
  }

  public set firstName(firstName: string) {
    this._firstName = firstName;
  }

  public get lastName(): string | undefined {
    return this._lastName;
  }

  public set lastName(lastName: string) {
    this._lastName = lastName;
  }

  public get password(): string | null | undefined {
    return this._password;
  }

  public set password(password: string) {
    this._password = password;
  }

  public get photoUrl(): string | undefined {
    return this._photoUrl;
  }

  public set photoUrl(url: string) {
    this._photoUrl = url;
  }

  /**
   * The identity provider's realm roles for this user — `org-admin`, `org-member`, and whatever else
   * the tenant declares.
   *
   * `KeycloakAuthService.getUserRoles()` has always been able to read `realm_access.roles`, but
   * nothing carried them onto the user, so no component could ask what the signed-in person may do
   * without reaching for the auth service and knowing it was the Keycloak one. That gap is what
   * `session-user.context.ts` documented; this closes it.
   *
   * Copied on write and returned frozen: a caller that mutated this list would be editing what the
   * identity provider said, and an authorization check reading it afterwards would be checking the
   * caller's own answer.
   */
  public get roles(): readonly string[] {
    return this._roles;
  }

  public set roles(roles: readonly string[]) {
    this._roles = Object.freeze([...roles]);
  }

  /** Whether the user holds `role`. A convenience over {@link roles}, not a security decision. */
  public hasRole(role: string): boolean {
    return this._roles.includes(role);
  }

  /** Whether the user holds at least one of `roles`. An empty list means "any authenticated user". */
  public hasAnyRole(roles: readonly string[]): boolean {
    return roles.length === 0 || roles.some((role) => this._roles.includes(role));
  }

  // endregion
}
