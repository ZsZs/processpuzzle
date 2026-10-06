import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, Observable, of, throwError } from 'rxjs';
import { CatalogStarter, ImportReport, InstalledStarter, isImportReport, StarterSelection } from './starter';

/**
 * The tenant-free root the catalog lives under: the organization-scoped root with its
 * `/organizations/<orgKey>` tail removed, since a starter belongs to no organization.
 */
export function catalogRootOf(organizationRoot: string): string {
  return organizationRoot.replace(/\/organizations\/[^/]+\/?$/, '');
}

/**
 * base-starter-backend's endpoints. The organization-scoped ones live under `STARTER_SERVICE_ROOT` (falling
 * back to `BACKEND_SERVICE_ROOT`, like every feature root); the catalog under the same root without its
 * organization segment.
 */
@Injectable({ providedIn: 'root' })
export class StarterService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'STARTER_SERVICE_ROOT');
  private readonly catalogRoot = catalogRootOf(this.root);

  listCatalog(): Observable<CatalogStarter[]> {
    return this.http.get<CatalogStarter[]>(`${this.catalogRoot}/starters`);
  }

  /**
   * Installs a catalog starter, as a dry run or for real. The backend fetches the bundle itself.
   *
   * A refused install is not an error here: the server answers 409 (the organization holds instance data),
   * 413 or 422 with the same `ImportReport` a successful one returns, status `rejected` and the reasons in
   * `errors`, and that report is what this emits. Only a failure that carries no report — a 403, a 404, a
   * 503, no network — errors.
   */
  install(selection: StarterSelection, dryRun: boolean): Observable<ImportReport> {
    return this.http.post<ImportReport>(`${this.root}/starters/install`, { ...selection, dryRun }).pipe(
      catchError((error: unknown) => (error instanceof HttpErrorResponse && isImportReport(error.error) ? of(error.error) : throwError(() => error))),
    );
  }

  listInstalled(): Observable<InstalledStarter[]> {
    return this.http.get<InstalledStarter[]>(`${this.root}/starters`);
  }
}
