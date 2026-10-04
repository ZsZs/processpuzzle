import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, map, Observable, of, throwError } from 'rxjs';
import { Enrollment } from './enrollment';

/**
 * The gallery of one subject.
 *
 * A plain HTTP service rather than a `BaseEntityRestService`: an enrollment is a projection over a subject
 * that belongs to another feature, with verbs of its own — read it, bring it in line with the subject's
 * photos, discard it — not a collection to list and edit. The photos themselves are edited where the subject
 * is edited; nothing here uploads one.
 */
@Injectable({ providedIn: 'root' })
export class EnrollmentService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'AI_SERVICE_ROOT');

  /**
   * The subject's enrollment, or `undefined` when its entity type has no recognition profile — the
   * backend's 404, which here is an ordinary answer rather than an error.
   *
   * @param entityName the profile's key, the entity definition code — e.g. `boat`
   */
  find(entityName: string, objectId: string): Observable<Enrollment | undefined> {
    return this.http.get<Enrollment>(this.enrollmentUrl(entityName, objectId)).pipe(
      map((enrollment) => normalize(enrollment)),
      catchError((error: unknown) => (error instanceof HttpErrorResponse && error.status === 404 ? of(undefined) : throwError(() => error))),
    );
  }

  /** Enrolls the subject's photos new since the last synchronization and drops the gone ones. Idempotent. */
  synchronize(entityName: string, objectId: string): Observable<Enrollment> {
    return this.http.post<Enrollment>(this.enrollmentUrl(entityName, objectId), null).pipe(map((enrollment) => normalize(enrollment)));
  }

  /** Discards the crops and embeddings — never the photos; the next synchronization enrolls them afresh. */
  deleteAll(entityName: string, objectId: string): Observable<void> {
    return this.http.delete<void>(this.enrollmentUrl(entityName, objectId));
  }

  private enrollmentUrl(entityName: string, objectId: string): string {
    return `${this.root}/entities/${encodeURIComponent(entityName)}/${encodeURIComponent(objectId)}/enrollment`;
  }
}

/** Defensive about the arrays and flags, as base-state's operation reads are. */
function normalize(enrollment: Enrollment): Enrollment {
  return {
    ...enrollment,
    photos: (enrollment?.photos ?? []).map((photo) => ({ ...photo, identifierMismatch: photo.identifierMismatch ?? false })),
  };
}
