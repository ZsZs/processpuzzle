import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map, Observable } from 'rxjs';
import { buildUrl } from 'build-url-ts';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

export interface UploadObjectResponse {
  objectID: string;
  fileName: string;
  mimeType: string;
  bucketName?: string;
}

export interface ObjectDownloadResponse {
  data: Blob;
  fileName?: string;
  bucketName?: string;
}

export interface ObjectUriResponse {
  uri: string;
}

@Injectable({ providedIn: 'root' })
export class ObjectStoreService {
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION);
  private readonly httpClient = inject(HttpClient);

  private readonly baseUrl: string = this.resolveBaseUrl();

  uploadObject(file: Blob, name: string, mimeType: string): Observable<UploadObjectResponse> {
    const formData = new FormData();
    formData.append('file', file, name);
    formData.append('name', name);
    formData.append('mimeType', mimeType);

    return this.httpClient.post<Omit<UploadObjectResponse, 'bucketName'>>(this.buildFullUrl('/objects'), formData, { observe: 'response' }).pipe(
      map((response) => {
        if (!response.body) throw new Error('Object store upload response body is missing');
        return {
          ...response.body,
          bucketName: response.headers.get('Location') ?? undefined,
        };
      }),
    );
  }

  getObjectByID(bucketName: string, objectID: string): Observable<ObjectDownloadResponse> {
    const path = `/objects/${encodeURIComponent(bucketName)}/${encodeURIComponent(objectID)}`;

    return this.httpClient.get(this.buildFullUrl(path), { observe: 'response', responseType: 'blob' }).pipe(
      map((response) => ({
        data: response.body ?? new Blob(),
        fileName: response.headers.get('X-Object-Name') ?? undefined,
        bucketName: response.headers.get('X-Object-Bucket') ?? undefined,
      })),
    );
  }

  deleteObjectByID(bucketName: string, objectID: string): Observable<void> {
    const path = `/objects/${encodeURIComponent(bucketName)}/${encodeURIComponent(objectID)}`;
    return this.httpClient.delete<void>(this.buildFullUrl(path));
  }

  getObjectUriByID(bucketName: string, objectID: string): Observable<ObjectUriResponse> {
    const path = `/objects/${encodeURIComponent(bucketName)}/${encodeURIComponent(objectID)}/uri`;
    return this.httpClient.get<ObjectUriResponse>(this.buildFullUrl(path));
  }

  private buildFullUrl(path: string): string {
    return buildUrl(this.baseUrl, { path });
  }

  private resolveBaseUrl(): string {
    const baseConfiguration = Reflect.get(this.runtimeConfiguration, 'BASE_CONFIGURATION');
    return Reflect.get(baseConfiguration, 'OBJECT_STORE_SERVICE_ROOT') ?? Reflect.get(baseConfiguration, 'BACKEND_SERVICE_ROOT') ?? '';
  }
}
