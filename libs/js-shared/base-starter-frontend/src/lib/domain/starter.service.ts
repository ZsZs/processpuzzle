import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, Observable, of, throwError } from 'rxjs';
import { ImportReport, InstalledStarter, isImportReport } from './starter';

/**
 * base-starter-backend's two endpoints, under the organization-scoped `STARTER_SERVICE_ROOT` (falling back
 * to `BACKEND_SERVICE_ROOT`, like every feature root).
 */
@Injectable({ providedIn: 'root' })
export class StarterService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'STARTER_SERVICE_ROOT');

  /**
   * Uploads a bundle zip, as a dry run or for real.
   *
   * A refused import is not an error here: the server answers 422 — or 413 for a bundle over a size limit —
   * with the same `ImportReport` a successful one returns, status `rejected` and the reasons in `errors`, and
   * that report is what this emits. Only a failure that carries no report — a 403, a 500, no network — errors.
   */
  importBundle(bundle: File | Blob, dryRun: boolean): Observable<ImportReport> {
    const body = new FormData();
    body.append('bundle', bundle, bundle instanceof File ? bundle.name : 'bundle.zip');
    const params = new HttpParams().set('dryRun', String(dryRun));
    return this.http.post<ImportReport>(`${this.root}/definitions/import`, body, { params }).pipe(
      catchError((error: unknown) => (error instanceof HttpErrorResponse && isImportReport(error.error) ? of(error.error) : throwError(() => error))),
    );
  }

  listInstalled(): Observable<InstalledStarter[]> {
    return this.http.get<InstalledStarter[]>(`${this.root}/starters`);
  }
}
