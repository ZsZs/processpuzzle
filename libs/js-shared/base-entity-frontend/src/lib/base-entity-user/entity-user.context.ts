import { Injectable, signal } from '@angular/core';

/**
 * The seam between base-entity and the host application's session: who is editing, for the controls that
 * record it — today the COMMENTS control, which stamps each comment with its author.
 *
 * base-entity cannot ask `@processpuzzle/auth` itself: auth depends on base-entity, not the other way round. So
 * the host provides an implementation over this default, the way it does for base-workflow's
 * `CurrentUserContext`:
 *
 * ```ts
 * providers: [{ provide: EntityUserContext, useClass: SessionEntityUserContext }]
 * ```
 *
 * The default is a live, empty session rather than an abstract class, so the library renders standalone; a
 * comment written without one is attributed to an unknown author.
 */
@Injectable({ providedIn: 'root' })
export class EntityUserContext {
  private readonly _userId = signal('');
  private readonly _displayName = signal('');

  /** Empty when nobody is signed in. */
  readonly userId = this._userId.asReadonly();
  /** The name a reader recognizes the user by — empty when the host does not know one. */
  readonly displayName = this._displayName.asReadonly();

  /** Called by the host's session integration — on sign-in, token refresh or sign-out. */
  set(session: { userId: string; displayName?: string }): void {
    this._userId.set(session.userId);
    this._displayName.set(session.displayName ?? '');
  }
}
