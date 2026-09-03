import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { Observable, map } from 'rxjs';
import { Organization } from './organization';
import { OrganizationMapper } from './organization.mapper';

/** The administrator returned by {@link OrganizationService.assignAdmin}. */
export interface AdminUser {
  id: string;
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  realm: string;
  roles?: string[];
}

/**
 * The `/platform/organizations` resource.
 *
 * Extends `BaseEntityRestService` so the generated list and form work unchanged, and adds the three
 * verbs that are not CRUD. Each is a separate operation rather than a field on the update payload
 * because each does something the database alone cannot: suspending disables the tenant's Keycloak
 * realm, activating re-enables it, and assigning an administrator creates a user in it.
 *
 * The extra URLs are built through the inherited `buildFullUrl` rather than composed here, so they
 * cannot drift from where the list and the form are talking — the kind of difference that shows up as
 * one screen working and the next one 404ing.
 */
@Injectable({ providedIn: 'root' })
export class OrganizationService extends BaseEntityRestService<Organization> {
  constructor(protected override entityMapper: OrganizationMapper) {
    super(entityMapper, 'PLATFORM_ADMIN_SERVICE_ROOT', 'platform/organizations');
  }

  /** Revokes the tenant's access and disables its realm. Idempotent. */
  suspend(orgKey: string): Observable<Organization> {
    return this.postVerb(orgKey, 'suspend');
  }

  /** Restores a suspended tenant. Refused with 409 for a tenant still `PROVISIONING`. */
  activate(orgKey: string): Observable<Organization> {
    return this.postVerb(orgKey, 'activate');
  }

  /**
   * Creates the tenant's administrator in its own realm and grants it `org-admin`.
   *
   * No password field, on purpose: the user is created without credentials and with a required
   * password reset, so whoever performs this never learns the invitee's password.
   */
  assignAdmin(orgKey: string, user: { username: string; email: string; firstName?: string; lastName?: string }): Observable<AdminUser> {
    return this.httpClient.post<AdminUser>(this.verbUrl(orgKey, 'admin-user'), user, { headers: this.headers });
  }

  private postVerb(orgKey: string, verb: string): Observable<Organization> {
    return this.httpClient
      .post<unknown>(this.verbUrl(orgKey, verb), {}, { headers: this.headers })
      .pipe(map((dto) => this.entityMapper.fromDto(dto)));
  }

  private verbUrl(orgKey: string, verb: string): string {
    const pathParams = new Map<string, string>([['id', orgKey]]);
    const url = this.buildFullUrl(`${this.resourceUrl}/%{id}/${verb}`, { pathParams });
    if (!url) throw new Error(`Could not determine the URL of ${verb} for organization '${orgKey}'.`);
    return url;
  }
}
