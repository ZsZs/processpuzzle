import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { OrganizationRole } from '../domain/organization-user';
import { OrganizationRoleService } from '../domain/organization-user.service';

/**
 * The role-assignment screen: the realm's roles as a checkbox set, saved as a whole.
 *
 * **A full replacement, not add and remove.** Two people editing this screen at once must not
 * silently merge into a union neither of them chose, so the save sends the complete set and the
 * backend computes the grants and revocations from the difference. It refuses the whole payload if
 * any name is not a role the realm declares, rather than minting one — a role created from a typo is
 * one that nothing in the platform ever matches.
 *
 * The roles come from the realm rather than from a fixed list, because beyond `org-admin` and
 * `org-member` they are the tenant's own and are what `NavNode.roles` and workflow role definitions
 * are matched against.
 *
 * A caveat worth showing the user: a grant lands in the person's token on **next login**. The API
 * answers immediately, but an already-issued token keeps the old roles until it is refreshed.
 */
@Component({
  selector: 'pp-role-assignment',
  standalone: true,
  imports: [CommonModule, MatButton, MatCheckbox, MatProgressBar],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate"></mat-progress-bar>
    } @else {
      <section class="role-assignment">
        <h3>Roles</h3>
        @if (realmRoles().length === 0) {
          <p class="role-assignment__empty">This organization's realm declares no roles.</p>
        }
        <!-- id, not attr.id: MatCheckbox binds id on its own host, so an attribute binding is
             overwritten by the generated mat-mdc-checkbox-N and nothing can address the box. -->
        @for (role of realmRoles(); track role.name) {
          <mat-checkbox [id]="'role-' + role.name" [checked]="selected().has(role.name)" [disabled]="saving()" (change)="toggle(role.name)">
            {{ role.name }}
            @if (role.platformManaged) {
              <span class="role-assignment__managed">(ProcessPuzzle)</span>
            }
            @if (role.description) {
              <span class="role-assignment__description">— {{ role.description }}</span>
            }
          </mat-checkbox>
        }
        <p class="role-assignment__note">A change takes effect in the user's token at their next sign-in.</p>
        <div class="role-assignment__actions">
          <button id="save-roles" type="button" mat-raised-button color="primary" [disabled]="saving() || !userId" (click)="onSave()">Save roles</button>
          <button id="revert-roles" type="button" mat-button [disabled]="saving()" (click)="onRevert()">Revert</button>
        </div>
      </section>
    }
  `,
  styles: [
    `
      .role-assignment {
        display: flex;
        flex-direction: column;
        row-gap: 6px;
        padding: 12px 16px;
      }

      .role-assignment__managed,
      .role-assignment__description {
        opacity: 0.6;
        font-size: 0.85rem;
        margin-left: 4px;
      }

      .role-assignment__note {
        margin: 12px 0 0;
        font-size: 0.85rem;
        opacity: 0.75;
      }

      .role-assignment__actions {
        display: flex;
        column-gap: 8px;
        margin-top: 8px;
      }
    `,
  ],
})
export class RoleAssignmentComponent implements OnInit {
  readonly realmRoles = signal<OrganizationRole[]>([]);
  readonly selected = signal<Set<string>>(new Set());
  readonly loading = signal(true);
  readonly saving = signal(false);

  /** The user whose roles are edited, read from the `:entityId` segment of the details route. */
  userId: string | undefined;

  private held: string[] = [];

  private readonly route = inject(ActivatedRoute);
  private readonly roleService = inject(OrganizationRoleService);
  private readonly snackBar = inject(MatSnackBar);

  ngOnInit(): void {
    // The id sits on an ancestor segment, not this route's own: the tab is a sibling of the details
    // route under `<entity>/<id>`, so the parameter is inherited rather than declared here.
    this.userId = this.route.snapshot.paramMap.get('entityId') ?? this.route.parent?.snapshot.paramMap.get('entityId') ?? undefined;
    this.reload();
  }

  toggle(roleName: string): void {
    const next = new Set(this.selected());
    if (next.has(roleName)) next.delete(roleName);
    else next.add(roleName);
    this.selected.set(next);
  }

  onRevert(): void {
    this.selected.set(new Set(this.held));
  }

  onSave(): void {
    if (!this.userId) return;
    this.saving.set(true);
    this.roleService.replace(this.userId, [...this.selected()]).subscribe({
      next: (roles) => {
        this.saving.set(false);
        // Adopt what the server reports rather than what was sent: a role revoked concurrently by
        // somebody else is absent from the response, and keeping the local set would show a grant
        // that no longer exists.
        this.held = roles.map((role) => role.name);
        this.selected.set(new Set(this.held));
        this.snackBar.open('Roles saved. They reach the user’s token at their next sign-in.', undefined, { duration: 6000 });
      },
      // Left to the HTTP error interceptor: it already opens a snackbar carrying the backend's
      // errorId, and a second one here would stack two messages for one failure.
      error: () => this.saving.set(false),
    });
  }

  private reload(): void {
    this.loading.set(true);
    this.roleService.findAll().subscribe({
      next: (roles) => {
        this.realmRoles.set(roles);
        if (!this.userId) {
          this.loading.set(false);
          return;
        }
        this.roleService.findByUser(this.userId).subscribe({
          next: (userRoles) => {
            this.held = userRoles.map((role) => role.name);
            this.selected.set(new Set(this.held));
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => this.loading.set(false),
    });
  }
}
