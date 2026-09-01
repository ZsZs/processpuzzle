import { Injectable, signal } from '@angular/core';

/**
 * The seam between the dashboard and the host application's session.
 *
 * The dashboard needs two facts about whoever is looking at it — which user, and which roles — and
 * neither is base-workflow's to know. `@processpuzzle/auth` owns the session, and this library
 * deliberately does not depend on it: base-workflow's dependencies are base-state and util (see the
 * dependency graph in the workspace README), and a task list is not a reason to add an edge from a
 * feature library to the authentication one.
 *
 * So the host provides an implementation. The testbed's reads `AuthService.currentUser`; a Keycloak
 * deployment would read the token's claims:
 *
 * ```ts
 * providers: [{ provide: CurrentUserContext, useClass: SessionUserContext }]
 * ```
 *
 * The **organization is not here**, unlike in the design proposal: in this workspace the tenant is
 * part of the configured service root (`WORKFLOW_SERVICE_ROOT`, falling back to `APP_SERVICE_ROOT` —
 * `http://localhost:8080/organizations/processpuzzle-testbed` in dev), so every request is already
 * organization-scoped and no screen threads an `orgKey` through its calls. Putting one here would
 * invite a second, disagreeing source of the same fact.
 *
 * The default is a live, empty session rather than an abstract class, so the library renders standalone
 * — and what it renders when nothing has been provided is stated in {@link mayHoldRole}.
 */
@Injectable({ providedIn: 'root' })
export class CurrentUserContext {
  private readonly _userId = signal<string>('');
  private readonly _roles = signal<readonly string[]>([]);

  /** Empty when nobody is signed in; the inbox then shows no rows of its own, which is correct. */
  readonly userId = this._userId.asReadonly();

  /**
   * Whatever the host calls this user's roles — a `RoleDefinition.id`, its `name`, or the
   * `entityRoleId` it points at in base-entity's registry. The dashboard matches against all three
   * rather than insisting on one, because which of them a session carries is the host's choice and
   * base-workflow cannot narrow it. See {@link mayHoldRole} and {@link hasRole}.
   */
  readonly roles = this._roles.asReadonly();

  /**
   * Whether this session might hold one of the named roles — the **permissive** question, asked of the
   * Team queue to decide whether a task is worth offering.
   *
   * **An empty role list means "unknown", not "none".** A host that has not wired roles yet — the
   * testbed, today — would otherwise get a Team queue that is permanently empty and indistinguishable
   * from a queue with nothing in it, which hides the screen rather than restricting it. So an unknown
   * session sees every claimable task, and the backend stays the authority: `assignTask` refuses a user
   * who does not hold the task's role (`RoleDefinition.entityRoleId`, validated against base-entity's
   * role membership query), so a claim this screen offered too generously fails there rather than
   * succeeding wrongly.
   *
   * That is the whole reason this is separate from {@link hasRole}. Being permissive about *what to
   * show in a queue* is helpful; being permissive about *who may override* is not, and one predicate
   * doing both would make the Skip button appear for everybody the moment a host left roles unwired.
   */
  mayHoldRole(...roleNames: readonly (string | undefined)[]): boolean {
    if (this._roles().length === 0) return true;
    return roleNames.some((roleName) => roleName !== undefined && this._roles().includes(roleName));
  }

  /**
   * Whether this session **is stated** to hold the named role — the strict question, asked before
   * offering an override. An unknown session holds nothing: a host that has not wired roles gets no Skip
   * button, which is the safe direction for an action whose whole point is to bypass a rule.
   */
  hasRole(roleName: string): boolean {
    return this._roles().includes(roleName);
  }

  /** Called by the host's session integration — on sign-in, token refresh or organization switch. */
  set(session: { userId: string; roles?: readonly string[] }): void {
    this._userId.set(session.userId);
    this._roles.set(session.roles ?? []);
  }
}

/**
 * The role that may skip a task.
 *
 * Skip is a manager override rather than a peer of Complete — `skipTask` is documented as one in the
 * contract — so the button is gated instead of shown beside Complete for everyone. The name is a
 * placeholder in the sense that no ProcessPuzzle-wide role vocabulary exists yet; it is *not* a
 * security boundary. Nothing stops a determined caller from posting to `/skip`, and nothing here
 * pretends otherwise: this hides an action from users it would only confuse.
 */
export const PROCESS_OWNER_ROLE = 'process-owner';
