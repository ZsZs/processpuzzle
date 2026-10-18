import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { definitionRead, RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, forkJoin, map, Observable, of } from 'rxjs';

/** One object an entity parameter can be bound to: its id, and what to call it. */
export interface EntityChoice {
  readonly id: string;
  readonly label: string;
}

/** How many objects the picker offers; beyond that the id is typed in. */
export const ENTITY_CHOICES_LIMIT = 100;

interface EntityObjectDto {
  id: string;
  payload?: Record<string, unknown>;
}

interface EntityAttributeDto {
  code: string;
  isLinkToDetails?: boolean;
}

/**
 * The objects of an entity type, for the Evaluate tab's parameter picker. Reads base-entity's resources directly —
 * the operation layer's paged collection and the definition, for the attribute that titles an object — rather than
 * through base-entity's services, which are built around one facade per type.
 *
 * Answers an empty list on any failure, and for a type with more than {@link ENTITY_CHOICES_LIMIT} objects: the tab
 * then falls back to a plain id field, which is what it had before.
 */
@Injectable({ providedIn: 'root' })
export class EntityChoicesService {
  private readonly http = inject(HttpClient);
  private readonly serviceRoot = serviceRootOf(inject(RUNTIME_CONFIGURATION, { optional: true }), 'ENTITY_SERVICE_ROOT');

  choicesOf(entity: string): Observable<EntityChoice[]> {
    if (!this.serviceRoot) return of([]);
    const code = encodeURIComponent(entity);
    const objects = this.http.get<{ content?: EntityObjectDto[]; totalElements?: number }>(`${this.serviceRoot}/entities/${code}`, {
      params: { page: 0, size: ENTITY_CHOICES_LIMIT },
    });
    const titleAttribute = this.http
      .get<{ attributes?: EntityAttributeDto[] }>(`${this.serviceRoot}/entity-definitions/${code}`, { context: definitionRead() })
      .pipe(
        map((definition) => definition.attributes?.find((attribute) => attribute.isLinkToDetails)?.code),
        catchError(() => of(undefined)),
      );
    return forkJoin([objects, titleAttribute]).pipe(
      map(([page, title]) => ((page.totalElements ?? 0) > ENTITY_CHOICES_LIMIT ? [] : (page.content ?? []).map((object) => choiceOf(object, title)))),
      catchError(() => of([])),
    );
  }
}

function choiceOf(object: EntityObjectDto, titleAttribute: string | undefined): EntityChoice {
  const title = titleAttribute ? object.payload?.[titleAttribute] : undefined;
  const isTitle = typeof title === 'string' || typeof title === 'number' || typeof title === 'boolean';
  return { id: object.id, label: isTitle && title !== '' ? String(title) : object.id };
}
