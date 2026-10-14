import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, map, Observable, of, throwError } from 'rxjs';
import { isPendingRelease, PendingRelease, Release } from './release';

/** What publishing answered: the release it made, or the pending state that refused it. */
export type PublishOutcome = { released: Release } | { refused: PendingRelease };

/**
 * base-release-backend's endpoints, under `RELEASE_SERVICE_ROOT` — falling back to `BACKEND_SERVICE_ROOT`,
 * like every feature root, so the organization is part of the configured root.
 */
@Injectable({ providedIn: 'root' })
export class ReleaseService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'RELEASE_SERVICE_ROOT');

  /** The dry run: every pending definition and every problem, without releasing anything. */
  pending(): Observable<PendingRelease> {
    return this.http.get<PendingRelease>(`${this.root}/releases/pending`);
  }

  /**
   * Releases every pending definition, or nothing. A refusal is not an error here: the server answers 409 with
   * the pending state that refused it, and that is what this emits. Only a failure without that body errors.
   */
  publish(acknowledgeWarnings: boolean): Observable<PublishOutcome> {
    return this.http.post<Release>(`${this.root}/releases`, { acknowledgeWarnings }).pipe(
      map((released): PublishOutcome => ({ released })),
      catchError((error: unknown) =>
        error instanceof HttpErrorResponse && error.status === 409 && isPendingRelease(error.error) ? of<PublishOutcome>({ refused: error.error }) : throwError(() => error),
      ),
    );
  }

  /** The organization's releases, newest first. */
  history(): Observable<Release[]> {
    return this.http.get<Release[]>(`${this.root}/releases`);
  }
}
