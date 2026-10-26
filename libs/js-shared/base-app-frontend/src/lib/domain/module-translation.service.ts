import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { ModuleTranslationBundle, ModuleTranslationCoverage, ModuleTranslationUploadReport } from './module-translation';

/**
 * REST access to the prepared translation files of a module, under `/organizations/{orgKey}/modules/{key}`.
 * A service of its own rather than more methods on `ModuleDefinitionService`: those would inherit the JSON
 * `Content-Type` header of the generic CRUD client, which is wrong for a multipart upload and a zip download.
 */
@Injectable({ providedIn: 'root' })
export class ModuleTranslationService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION);

  /** The draft's coverage of every supported language. */
  coverage(moduleKey: string): Observable<ModuleTranslationCoverage[]> {
    return this.http.get<ModuleTranslationCoverage[]>(`${this.moduleUrl(moduleKey)}/translation-coverage`);
  }

  /** The zip of one prepared `<language>.json` per supported language. */
  download(moduleKey: string): Observable<Blob> {
    return this.http.get(`${this.moduleUrl(moduleKey)}/translations`, { responseType: 'blob' });
  }

  /** Stores the translated files — the zip, or one `<language>.json` — onto the draft. */
  upload(moduleKey: string, file: File): Observable<ModuleTranslationUploadReport> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.put<ModuleTranslationUploadReport>(`${this.moduleUrl(moduleKey)}/translations`, body);
  }

  /**
   * What the module's transloco scope loads for one language: the stored translations only, keys relative
   * to the scope. `released` is the running application's read, `draft` the designer's.
   */
  bundle(moduleKey: string, locale: string, revision: 'draft' | 'released'): Observable<ModuleTranslationBundle> {
    return this.http.get<ModuleTranslationBundle>(`${this.moduleUrl(moduleKey)}/translations/${encodeURIComponent(locale)}`, { params: { revision } });
  }

  private moduleUrl(moduleKey: string): string {
    return `${serviceRootOf(this.runtimeConfiguration, 'APP_SERVICE_ROOT')}/modules/${encodeURIComponent(moduleKey)}`;
  }
}
