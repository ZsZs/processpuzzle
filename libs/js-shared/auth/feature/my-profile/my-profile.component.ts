import { Component, computed, DOCUMENT, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatCard, MatCardActions, MatCardContent, MatCardHeader, MatCardTitle } from '@angular/material/card';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatChip } from '@angular/material/chips';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideTranslocoScope, TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { AUTHENTICATION_SERVICE, isIdentityProviderRole, ROLE_DESCRIPTION_SOURCE, type RoleDescription, SelfServiceRolesService } from '@processpuzzle/auth/domain';
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
 * The one thing it does change is the user's roles, and only when the application opts in with
 * `SELF_SERVICE_ROLES_SERVICE_ROOT` (see {@link SelfServiceRolesService}): then the roles the backend
 * offers are shown as checkboxes, and saving re-reads the user so the chips update without signing in
 * again. Without it the roles stay read-only chips.
 *
 * Roles are shown with what they are for: the platform's own from auth's translations, the tenant's
 * from {@link ROLE_DESCRIPTION_SOURCE} when the application binds one. Keycloak's built-in roles are
 * left out — they say nothing about what the user may do.
 */
@Component({
  selector: 'pp-my-profile',
  templateUrl: 'my-profile.component.html',
  styleUrls: ['my-profile.component.css'],
  imports: [MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatCardActions, MatFormField, MatLabel, MatInput, MatChip, MatCheckbox, MatButton, MatIcon, TranslocoDirective],
  providers: [provideTranslocoScope('auth')],
})
export class MyProfileComponent {
  private readonly authService = inject(AUTHENTICATION_SERVICE);
  private readonly document = inject(DOCUMENT);
  private readonly selfService = inject(SelfServiceRolesService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  readonly user = computed(() => this.authService.currentUser());
  private readonly roleDescriptions = toSignal(this.loadRoleDescriptions(), { initialValue: new Map<string, RoleDescription>() });
  readonly roles = computed<DisplayedRole[]>(() =>
    (this.user()?.roles ?? [])
      .filter((role) => !isIdentityProviderRole(role))
      .map((role) => this.display(role)),
  );
  readonly selectableRoles = signal<DisplayedRole[]>([]);
  private readonly baseline = signal<ReadonlySet<string>>(new Set());
  readonly selected = signal<ReadonlySet<string>>(new Set());
  readonly saving = signal(false);
  readonly dirty = computed(() => {
    const now = this.selected();
    const before = this.baseline();
    return now.size !== before.size || [...now].some((role) => !before.has(role));
  });
  readonly accountManagementUrl = computed(() => (this.user() ? this.authService.getAccountManagementUrl(this.document.location.href) : undefined));

  constructor() {
    if (this.selfService.available) void this.loadSelectableRoles();
  }

  toggle(role: string, checked: boolean): void {
    const next = new Set(this.selected());
    if (checked) next.add(role);
    else next.delete(role);
    this.selected.set(next);
  }

  async saveRoles(): Promise<void> {
    this.saving.set(true);
    try {
      const result = await this.selfService.replace([...this.selected()]);
      this.applySelfService(result.assigned, result.selectable);
      await this.authService.reloadUser();
      this.notify('auth.my_profile_form.roles_saved');
    } catch {
      this.notify('auth.my_profile_form.roles_save_failed', true);
    } finally {
      this.saving.set(false);
    }
  }

  /** A failing or refusing backend leaves the section out: the roles stay readable as chips. */
  private async loadSelectableRoles(): Promise<void> {
    try {
      const result = await this.selfService.load();
      this.applySelfService(result.assigned, result.selectable);
    } catch {
      this.selectableRoles.set([]);
    }
  }

  private applySelfService(assigned: readonly string[], selectable: readonly { name: string; description?: string }[]): void {
    this.selectableRoles.set(selectable.map((role) => this.display(role.name, role.description)));
    const chosen = new Set(selectable.map((role) => role.name).filter((name) => assigned.includes(name)));
    this.baseline.set(chosen);
    this.selected.set(new Set(chosen));
  }

  private notify(key: string, isError = false): void {
    const close = this.transloco.translate('auth.my_profile_form.close_button');
    this.snackBar.open(this.transloco.translate(key), close, isError ? { duration: 5000, panelClass: ['error-snackbar'] } : { duration: 5000 });
  }

  private display(role: string, backendDescription?: string): DisplayedRole {
    const known = this.roleDescriptions().get(role);
    const platformKey = PLATFORM_ROLE_KEYS[role];
    return {
      id: role,
      name: known?.name || role,
      description: known?.description || backendDescription,
      descriptionKey: platformKey ? `auth.my_profile_form.platform_roles.${platformKey}` : undefined,
    };
  }

  /** Descriptions are a nicety: a missing source or a failing one leaves the roles shown by id. */
  private loadRoleDescriptions(): Observable<ReadonlyMap<string, RoleDescription>> {
    const source = inject(ROLE_DESCRIPTION_SOURCE, { optional: true });
    if (!source) return of(new Map());
    return source.describeRoles().pipe(catchError(() => of(new Map<string, RoleDescription>())));
  }
}
