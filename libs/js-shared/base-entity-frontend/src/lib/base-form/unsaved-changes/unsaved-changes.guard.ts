import { CanDeactivateFn } from '@angular/router';

/** Implemented by a screen that can tell whether leaving it would lose the user's edits. */
export interface HasUnsavedChanges {
  /** Resolves true when it is fine to leave — nothing to lose, or the user chose to discard it. */
  confirmLeave(): boolean | Promise<boolean>;
}

/** Reminds the user of unsaved changes before they navigate away from an entity form. */
export const unsavedChangesGuard: CanDeactivateFn<HasUnsavedChanges> = (component) => component.confirmLeave();
