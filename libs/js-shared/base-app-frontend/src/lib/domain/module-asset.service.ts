import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { Observable } from 'rxjs';
import { ModuleAsset } from './module-asset';

/**
 * REST access to the assets of a module, under `/organizations/{orgKey}/modules/{key}/assets`. A service of its
 * own, for the reason `ModuleTranslationService` is: the generic CRUD client sends a JSON `Content-Type`, which is
 * wrong for a multipart upload and for a file download.
 */
@Injectable({ providedIn: 'root' })
export class ModuleAssetService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION);

  list(moduleKey: string, revision: 'draft' | 'released' = 'draft'): Observable<ModuleAsset[]> {
    return this.http.get<ModuleAsset[]>(this.assetsUrl(moduleKey), { params: { revision } });
  }

  /** Adds the file to the module draft under `path`, replacing the asset already there; its own name when `path` is empty. */
  upload(moduleKey: string, file: File, path?: string): Observable<ModuleAsset> {
    const body = new FormData();
    body.append('file', file, file.name);
    if (path) body.append('path', path);
    return this.http.post<ModuleAsset>(this.assetsUrl(moduleKey), body);
  }

  remove(moduleKey: string, path: string): Observable<void> {
    return this.http.delete<void>(this.assetsUrl(moduleKey), { params: { path } });
  }

  /** The bytes of one asset. Through `HttpClient`, so that the bearer token goes with it. */
  content(moduleKey: string, path: string, revision: 'draft' | 'released'): Observable<Blob> {
    return this.http.get(`${this.assetsUrl(moduleKey)}/content`, { params: { path, revision }, responseType: 'blob' });
  }

  private assetsUrl(moduleKey: string): string {
    return `${serviceRootOf(this.runtimeConfiguration, 'APP_SERVICE_ROOT')}/modules/${encodeURIComponent(moduleKey)}/assets`;
  }
}
