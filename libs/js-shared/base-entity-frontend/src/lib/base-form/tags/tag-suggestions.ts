import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

/** The suggestion source of the realm's role catalog, for attributes that hold role names. */
export const REALM_ROLE_SUGGESTIONS = 'realm-roles';

/**
 * Where a `TAGS` control gets values to offer, named by the attribute's `tagSuggestions`.
 *
 * Suggestions only help: the control still accepts any text, so a source that fails or answers empty
 * leaves a plain free-text control. base-entity names no source itself — the application binds the ones
 * it has, with `multi: true`, for example the role catalog under {@link REALM_ROLE_SUGGESTIONS}.
 */
export interface TagSuggestionSource {
  readonly key: string;
  suggestions(): Observable<readonly string[]>;
}

export const TAG_SUGGESTION_SOURCES = new InjectionToken<readonly TagSuggestionSource[]>('TAG_SUGGESTION_SOURCES');
