import { inject, Injectable, InjectionToken } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BaseEntityRestService, PersistedEntity } from '@processpuzzle/base-entity';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { Observable, map } from 'rxjs';
import { OrganizationRole, OrganizationUser, OrganizationUserInvitation } from './organization-user';
import { OrganizationRoleMapper, OrganizationUserMapper } from './organization-user.mapper';

/**
 * The tenant that these services address.
 *
 * Every org-admin path is `/organizations/{orgKey}/admin/...`, so the resource URL cannot be a
 * constant the way `rules` or `platform/organizations` is — it carries the tenant. The key is
 * supplied once, by the host application, from the URL segment it also resolved the Keycloak realm
 * from; see `resolveTenantRealm` in `@processpuzzle/auth`.
 *
 * A provider rather than a constructor argument because `BaseEntityRestService` builds its resource
 * URL at construction, and the services are `providedIn: 'root'` singletons resolved by the
 * generated screens — there is no call site to pass a parameter at.
 */
export const ORG_ADMIN_ORG_KEY = new InjectionToken<string>('ORG_ADMIN_ORG_KEY');

/**
 * `/organizations/{orgKey}/admin/users`.
 *
 * The paths sit under `/admin` and not directly under the organization because base-workflow already
 * owns `/organizations/{orgKey}/roles` for workflow role definitions — a different thing from a realm
 * role, and mounting both at one URL is an ambiguous mapping the backend refuses to start with. See
 * org-admin-api.yaml.
 *
 * `filterParamName` is renamed to `search`: this resource has no database behind it, so the backend
 * passes the parameter to the identity provider's own free-text user search rather than parsing RSQL.
 * Renaming rather than reimplementing is the same move `DynamicEntityService` makes for base-entity's
 * `rsql` / `sort` pair.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationUserService extends BaseEntityRestService<OrganizationUser> {
  protected override readonly filterParamName = 'search';

  constructor(protected override entityMapper: OrganizationUserMapper) {
    super(entityMapper, 'ORG_ADMIN_SERVICE_ROOT', `organizations/${inject(ORG_ADMIN_ORG_KEY)}/admin/users`);
  }

  /**
   * Invites a user, POSTing `OrganizationUserInvite`.
   *
   * Not `add`, and this is the reason: `add` sends whatever `toDto` produces, and `toDto` models the
   * *update* payload - it drops `username` and `roles` deliberately, so that a name correction can
   * never rewrite somebody's permissions. Inviting through it would post neither, and `username` is
   * required by the contract, so every invitation would come back 400. The two payloads differ in both
   * directions, so the invitation gets its own method rather than a flag on the mapper.
   */
  invite(invitation: OrganizationUserInvitation): Observable<PersistedEntity<OrganizationUser>> {
    const fullUrl = this.buildFullUrl(this.resourceUrl, {});
    if (!fullUrl) throw new Error('Could not determine the full url');
    return this.httpClient
      .post(fullUrl, invitation, { headers: this.headers })
      .pipe(map((response) => this.entityMapper.fromDto(response) as PersistedEntity<OrganizationUser>));
  }
}

/**
 * `/organizations/{orgKey}/admin/roles` and the per-user role assignment.
 *
 * Not a `BaseEntityRestService` for the assignment half: replacing a user's roles is a PUT to a
 * sub-resource of a user, which the generic CRUD service has no shape for. The role list itself is a
 * plain read.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationRoleService {
  private readonly httpClient = inject(HttpClient);
  private readonly mapper = inject(OrganizationRoleMapper);
  private readonly orgKey = inject(ORG_ADMIN_ORG_KEY);
  private readonly baseUrl = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'ORG_ADMIN_SERVICE_ROOT');

  /** Every realm role the tenant declares, read live rather than from a fixed list. */
  findAll(): Observable<OrganizationRole[]> {
    return this.httpClient.get<unknown[]>(this.adminUrl('roles')).pipe(map((dtos) => dtos.map((dto) => this.mapper.fromDto(dto))));
  }

  /** The roles one user currently holds. */
  findByUser(userId: string): Observable<OrganizationRole[]> {
    return this.httpClient.get<unknown[]>(this.adminUrl(`users/${userId}/roles`)).pipe(map((dtos) => dtos.map((dto) => this.mapper.fromDto(dto))));
  }

  /**
   * Makes the user hold exactly `roles`.
   *
   * A full replacement rather than add and remove calls, because the assignment screen presents the
   * realm's roles as a checkbox set and saves the whole set — and two people editing that screen at
   * once must not silently merge into a union neither of them chose. The backend computes the grants
   * and revocations from the difference, and refuses the whole payload if any name is not a role the
   * realm declares.
   */
  replace(userId: string, roles: readonly string[]): Observable<OrganizationRole[]> {
    return this.httpClient
      .put<unknown[]>(this.adminUrl(`users/${userId}/roles`), { roles: [...roles] })
      .pipe(map((dtos) => dtos.map((dto) => this.mapper.fromDto(dto))));
  }

  private adminUrl(suffix: string): string {
    return `${this.baseUrl}/organizations/${this.orgKey}/admin/${suffix}`;
  }
}
