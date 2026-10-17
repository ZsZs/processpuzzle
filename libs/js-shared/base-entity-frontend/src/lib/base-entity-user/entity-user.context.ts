import { computed, inject, Injectable, signal } from '@angular/core';
import { SessionUser } from '@processpuzzle/util';

/**
 * The seam between base-entity and the host application's session: who is editing, for the controls that
 * record it — today the COMMENTS control, which stamps each comment with its author.
 *
 * base-entity cannot ask `@processpuzzle/auth` itself: auth depends on base-entity, not the other way round. So it
 * reads util's `SessionUser`, which auth keeps current — nothing to wire in a host that uses auth. A host
 * without it may call {@link set}, which then takes precedence, or provide its own implementation.
 *
 * Without either, a comment is attributed to an unknown author.
 */
@Injectable({ providedIn: 'root' })
export class EntityUserContext {
  private readonly sessionUser = inject(SessionUser);
  private readonly _session = signal<{ userId: string; displayName: string } | undefined>(undefined);

  /** Empty when nobody is signed in. */
  readonly userId = computed(() => this._session()?.userId ?? this.sessionUser.userId());
  /** The name a reader recognizes the user by — empty when the host does not know one. */
  readonly displayName = computed(() => this._session()?.displayName ?? this.sessionUser.displayName());

  /** For a host whose session is not auth's — overrides `SessionUser` from then on. */
  set(session: { userId: string; displayName?: string }): void {
    this._session.set({ userId: session.userId, displayName: session.displayName ?? '' });
  }
}
