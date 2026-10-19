import { InjectionToken, Signal } from '@angular/core';

/**
 * What the client knows about the current user, for deciding which document and book actions to *offer*.
 * Never what decides whether they are allowed — the backend's access ladder does, and answers 403.
 *
 * Optional on purpose: base-document does not depend on an authentication library, so the application
 * bridges its own (the testbed provides it from `@processpuzzle/auth`). Absent, every action is offered and
 * the backend's answer is what the user sees.
 */
export interface DocumentAccessContext {
  /** The roles the user holds. */
  readonly roles: Signal<readonly string[]>;
  /** Whether anyone is signed in; taken as true when left out. */
  readonly authenticated?: Signal<boolean>;
}

export const DOCUMENT_ACCESS_CONTEXT = new InjectionToken<DocumentAccessContext>('DOCUMENT_ACCESS_CONTEXT');

/**
 * Whether the user may edit something guarded by `editorRoles` — the client half of the backend's rule: an empty
 * list means any signed-in member.
 */
export function mayEdit(context: DocumentAccessContext | null | undefined, editorRoles: readonly string[]): boolean {
  if (!context) return true;
  if (context.authenticated && !context.authenticated()) return false;
  if (editorRoles.length === 0) return true;
  const held = context.roles();
  return editorRoles.some((role) => held.includes(role));
}
