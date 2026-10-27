import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { NavigateBackService } from '@processpuzzle/util';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatProgressBar } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, distinctUntilChanged, EMPTY, forkJoin, map, merge, Observable, of, Subject, switchMap, tap } from 'rxjs';
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
 * **Editable only from a complete read.** The checkboxes are the authoritative baseline for a save,
 * so the screen is either loading, failed (with a retry), or loaded. A failed read must never look
 * like an empty set the administrator chose: saving that would revoke roles the user was never shown.
 *
 * **Follows the user in the URL.** The route can be reused for another user, so each change of the
 * inherited id starts a fresh load and discards the answers still in flight for the previous user.
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
    } @else if (failed()) {
      <section class="role-assignment">
        <h3>Roles</h3>
        <p class="role-assignment__error">The roles could not be read, so they cannot be changed yet.</p>
        <div class="role-assignment__actions">
          <button id="retry-roles" type="button" mat-raised-button color="primary" (click)="onRetry()">Try again</button>
        </div>
      </section>
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

      .role-assignment__error {
        margin: 0;
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
  readonly failed = signal(false);
  readonly saving = signal(false);

  /** The user whose roles are edited, read from the `:entityId` segment of the details route. */
  userId: string | undefined;

  private held: string[] = [];
  private readonly retries = new Subject<void>();

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly navigateBack = inject(NavigateBackService);
  private readonly roleService = inject(OrganizationRoleService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    // The tab's own route is `:entityId/roles`, so the id is this route's parameter. Reading it from the
    // parent finds nothing: the parent is the users route, which has no `entityId`, and Save stays disabled.
    const userIds = this.route.paramMap.pipe(
      map((params) => params.get('entityId') ?? undefined),
      distinctUntilChanged(),
    );
    merge(userIds, this.retries.pipe(map(() => this.userId)))
      .pipe(
        switchMap((userId) => {
          this.userId = userId;
          return this.load(userId);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  toggle(roleName: string): void {
    const next = new Set(this.selected());
    if (next.has(roleName)) next.delete(roleName);
    else next.add(roleName);
    this.selected.set(next);
  }

  onRevert(): void {
    this.selected.set(new Set(this.held));
    this.leave();
  }

  onRetry(): void {
    this.retries.next();
  }

  onSave(): void {
    if (!this.userId || this.loading() || this.failed()) return;
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
        this.leave();
      },
      // Left to the HTTP error interceptor: it already opens a snackbar carrying the backend's
      // errorId, and a second one here would stack two messages for one failure.
      error: () => this.saving.set(false),
    });
  }

  /**
   * Back to where the user came from, as NavigateBackService knows it. A page opened directly has no
   * history to return to, so the user's details are the destination instead.
   */
  private leave(): void {
    if (this.navigateBack.getRouteStack().size() > 1) this.navigateBack.goBack();
    else void this.router.navigate(['../details'], { relativeTo: this.route });
  }

  /**
   * Both reads for one user. An unreadable user still lists the realm's roles, but nothing is checked
   * and nothing can be saved: the baseline is unknown.
   */
  private load(userId: string | undefined): Observable<unknown> {
    this.loading.set(true);
    this.failed.set(false);
    return forkJoin({
      realm: this.roleService.findAll(),
      held: userId ? this.roleService.findByUser(userId) : of<OrganizationRole[]>([]),
    }).pipe(
      tap(({ realm, held }) => {
        this.realmRoles.set(realm);
        this.held = held.map((role) => role.name);
        this.selected.set(new Set(this.held));
        this.loading.set(false);
      }),
      catchError(() => {
        this.failed.set(true);
        this.loading.set(false);
        // Left to the HTTP error interceptor for the message; the retry is the screen's own business.
        return EMPTY;
      }),
    );
  }
}
