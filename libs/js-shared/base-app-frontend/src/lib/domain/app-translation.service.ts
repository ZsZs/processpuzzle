import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { AppTranslationBundle, AppTranslationUploadReport } from './app-translation';

/**
 * REST access to the translation files of an application, under `/organizations/{orgKey}/app-definitions/{appId}`.
 * A service of its own, for the reason {@link ModuleTranslationService} is: the generic CRUD client sends a JSON
 * `Content-Type`, which is wrong for a multipart upload and a zip download.
 */
@Injectable({ providedIn: 'root' })
export class AppTranslationService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION);

  /** The zip of one `<language>.json` per supported language, holding what the draft has stored. */
  download(appId: string): Observable<Blob> {
    return this.http.get(`${this.translationsUrl(appId)}`, { responseType: 'blob' });
  }

  /** Stores the translated files — the zip, or one `<language>.json` — onto the draft, replacing each language they name. */
  upload(appId: string, file: File): Observable<AppTranslationUploadReport> {
    const body = new FormData();
    body.append('file', file, file.name);
    return this.http.put<AppTranslationUploadReport>(this.translationsUrl(appId), body);
  }

  /** Removes the draft bundle of one language. */
  remove(appId: string, locale: string): Observable<void> {
    return this.http.delete<void>(`${this.translationsUrl(appId)}/${encodeURIComponent(locale)}`);
  }

  /**
   * The stored texts of one language, flat. `released` is the running application's read, `draft` the designer's.
   */
  bundle(appId: string, locale: string, revision: 'draft' | 'released'): Observable<AppTranslationBundle> {
    return this.http.get<AppTranslationBundle>(`${this.translationsUrl(appId)}/${encodeURIComponent(locale)}`, { params: { revision } });
  }

  private translationsUrl(appId: string): string {
    return `${serviceRootOf(this.runtimeConfiguration, 'APP_SERVICE_ROOT')}/app-definitions/${encodeURIComponent(appId)}/translations`;
  }
}
