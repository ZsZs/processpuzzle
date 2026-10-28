import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { Observable } from 'rxjs';
import { AppAsset } from './app-asset';

/**
 * REST access to the assets of an application, under `/organizations/{orgKey}/app-definitions/{appId}/assets`. A
 * service of its own, for the reason {@link ModuleAssetService} is: the generic CRUD client sends a JSON
 * `Content-Type`, which is wrong for a multipart upload and for a file download.
 */
@Injectable({ providedIn: 'root' })
export class AppAssetService {
  private readonly http = inject(HttpClient);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION);

  list(appId: string, revision: 'draft' | 'released' = 'draft'): Observable<AppAsset[]> {
    return this.http.get<AppAsset[]>(this.assetsUrl(appId), { params: { revision } });
  }

  /** Adds the file to the application draft under `path`, replacing the asset already there; its own name when `path` is empty. */
  upload(appId: string, file: File, path?: string): Observable<AppAsset> {
    const body = new FormData();
    body.append('file', file, file.name);
    if (path) body.append('path', path);
    return this.http.post<AppAsset>(this.assetsUrl(appId), body);
  }

  remove(appId: string, path: string): Observable<void> {
    return this.http.delete<void>(this.assetsUrl(appId), { params: { path } });
  }

  /** The bytes of one asset. Through `HttpClient`, so that the bearer token goes with it. */
  content(appId: string, path: string, revision: 'draft' | 'released'): Observable<Blob> {
    return this.http.get(`${this.assetsUrl(appId)}/content`, { params: { path, revision }, responseType: 'blob' });
  }

  private assetsUrl(appId: string): string {
    return `${serviceRootOf(this.runtimeConfiguration, 'APP_SERVICE_ROOT')}/app-definitions/${encodeURIComponent(appId)}/assets`;
  }
}
