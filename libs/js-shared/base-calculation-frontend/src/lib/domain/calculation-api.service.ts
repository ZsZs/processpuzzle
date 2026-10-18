import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { Observable } from 'rxjs';
import type { CalculationRun, EvaluationResult, Revision, TraceEntry, ValidationReport } from './calculation-api';

/**
 * The calculation verbs the generic CRUD cannot express: validate a source, evaluate, run, and read and freeze
 * runs. Not a `BaseEntityRestService` — none of these is a collection of one entity — but rooted where every other
 * service of this library is, through `serviceRootOf` and `CALCULATION_SERVICE_ROOT`, so the tenant stays a
 * deployment concern.
 */
@Injectable({ providedIn: 'root' })
export class CalculationApiService {
  private readonly http = inject(HttpClient);
  private readonly serviceRoot = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'CALCULATION_SERVICE_ROOT');
  private readonly headers = new HttpHeaders({ 'Content-Type': 'application/json; charset=utf-8' });

  /** `POST /calculation-definitions/validate` — a source with errors is a 200 with `valid: false`. */
  validate(source: string): Observable<ValidationReport> {
    return this.http.post<ValidationReport>(`${this.serviceRoot}/calculation-definitions/validate`, { source }, { headers: this.headers });
  }

  /**
   * `POST /calculations/{id}/evaluate` — computes and stores nothing.
   *
   * @param source an unsaved source to evaluate instead of the stored draft — the editor's preview
   */
  evaluate(calculationId: string, parameters: Record<string, unknown>, revision: Revision = 'draft', source?: string): Observable<EvaluationResult> {
    return this.http.post<EvaluationResult>(`${this.calculationUrl(calculationId)}/evaluate`, { parameters, revision, source }, { headers: this.headers });
  }

  /** `POST /calculations/{id}/runs` — computes, writes the output and keeps the run. */
  run(calculationId: string, parameters: Record<string, unknown>, revision: Revision = 'released'): Observable<CalculationRun> {
    return this.http.post<CalculationRun>(`${this.calculationUrl(calculationId)}/runs`, { parameters, revision }, { headers: this.headers });
  }

  /** `GET /calculation-runs`, newest first, optionally of one calculation or one subject. */
  runs(calculationId?: string, subjectId?: string): Observable<CalculationRun[]> {
    const params: Record<string, string> = {};
    if (calculationId) params['calculationId'] = calculationId;
    if (subjectId) params['subjectId'] = subjectId;
    return this.http.get<CalculationRun[]>(`${this.serviceRoot}/calculation-runs`, { params });
  }

  findRun(runId: string): Observable<CalculationRun> {
    return this.http.get<CalculationRun>(this.runUrl(runId));
  }

  trace(runId: string): Observable<TraceEntry[]> {
    return this.http.get<TraceEntry[]>(`${this.runUrl(runId)}/trace`);
  }

  /** `POST /calculation-runs/{id}/freeze` — idempotent; a failed run is refused with 409. */
  freeze(runId: string): Observable<CalculationRun> {
    return this.http.post<CalculationRun>(`${this.runUrl(runId)}/freeze`, {}, { headers: this.headers });
  }

  private calculationUrl(calculationId: string): string {
    return `${this.serviceRoot}/calculations/${encodeURIComponent(calculationId)}`;
  }

  private runUrl(runId: string): string {
    return `${this.serviceRoot}/calculation-runs/${encodeURIComponent(runId)}`;
  }
}
