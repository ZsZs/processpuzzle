import { Component, computed, DOCUMENT, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardActions, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatChip } from '@angular/material/chips';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { AUTHENTICATION_SERVICE, isIdentityProviderRole, ROLE_DESCRIPTION_SOURCE, type RoleDescription } from '@processpuzzle/auth/domain';
import { catchError, Observable, of } from 'rxjs';

/** The platform's own roles, described by auth's translations; the key is under `my_profile_form.platform_roles`. */
const PLATFORM_ROLE_KEYS: Readonly<Record<string, string>> = { 'org-admin': 'org_admin', 'org-member': 'org_member' };

interface DisplayedRole {
  id: string;
  name: string;
  description?: string;
  /** Translation key of the description, for the platform's own roles. */
  descriptionKey?: string;
}

/**
 * The signed-in user's own profile, read-only. Editing happens at the identity provider, which owns the
 * account; the page links there when the provider offers an account page (Keycloak's account console).
 *
 * Roles are shown with what they are for: the platform's own from auth's translations, the tenant's
 * from {@link ROLE_DESCRIPTION_SOURCE} when the application binds one. Keycloak's built-in roles are
 * left out — they say nothing about what the user may do.
 */
@Component({
  selector: 'pp-my-profile',
  templateUrl: 'my-profile.component.html',
  styleUrls: ['my-profile.component.css'],
  imports: [MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatCardActions, MatFormField, MatLabel, MatInput, MatChip, MatButton, MatIcon, TranslocoDirective],
  providers: [provideTranslocoScope('auth')],
})
export class MyProfileComponent {
  private readonly authService = inject(AUTHENTICATION_SERVICE);
  private readonly document = inject(DOCUMENT);
  readonly user = computed(() => this.authService.currentUser());
  private readonly roleDescriptions = toSignal(this.loadRoleDescriptions(), { initialValue: new Map<string, RoleDescription>() });
  readonly roles = computed<DisplayedRole[]>(() =>
    (this.user()?.roles ?? [])
      .filter((role) => !isIdentityProviderRole(role))
      .map((role) => {
        const known = this.roleDescriptions().get(role);
        const platformKey = PLATFORM_ROLE_KEYS[role];
        return {
          id: role,
          name: known?.name || role,
          description: known?.description,
          descriptionKey: platformKey ? `auth.my_profile_form.platform_roles.${platformKey}` : undefined,
        };
      }),
  );
  readonly accountManagementUrl = computed(() => (this.user() ? this.authService.getAccountManagementUrl(this.document.location.href) : undefined));

  /** Descriptions are a nicety: a missing source or a failing one leaves the roles shown by id. */
  private loadRoleDescriptions(): Observable<ReadonlyMap<string, RoleDescription>> {
    const source = inject(ROLE_DESCRIPTION_SOURCE, { optional: true });
    if (!source) return of(new Map());
    return source.describeRoles().pipe(catchError(() => of(new Map<string, RoleDescription>())));
  }
}
