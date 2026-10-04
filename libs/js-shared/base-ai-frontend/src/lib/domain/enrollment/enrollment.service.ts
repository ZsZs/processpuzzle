import { HttpBackend, HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { catchError, concatMap, from, lastValueFrom, map, Observable, of, throwError, toArray } from 'rxjs';
import { Enrollment, ENROLLMENT_PHOTO_TYPES, MediaUploadSlot } from './enrollment';

/**
 * The gallery of one subject, and the uploads that fill it.
 *
 * A plain HTTP service rather than a `BaseEntityRestService`: an enrollment is a projection over a subject
 * that belongs to another feature, with verbs of its own — add photos, remove one, remove all — not a
 * collection to list and edit.
 *
 * **Uploads go straight to object storage.** For each file, `createMediaUpload` reserves a slot and answers
 * a presigned PUT URL; the file is PUT there; then the slots' media keys are handed to `addEnrollmentPhotos`.
 * The PUT is sent through a bare `HttpClient` on `HttpBackend`, past every interceptor: the URL is signed,
 * and a header an interceptor added — an `Authorization`, say — would invalidate the signature.
 */
@Injectable({ providedIn: 'root' })
export class EnrollmentService {
  private readonly http = inject(HttpClient);
  private readonly storage = new HttpClient(inject(HttpBackend));
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

  /**
   * Uploads the files one after another and enrolls them in one call. Sequential rather than parallel: a
   * phone on a marina's network uploads faster one file at a time than five competing for the same link,
   * and `onProgress` can then say honestly how far it got.
   */
  async addPhotos(entityName: string, objectId: string, files: readonly File[], onProgress?: (uploaded: number) => void): Promise<Enrollment> {
    const accepted = files.filter((file) => ENROLLMENT_PHOTO_TYPES.includes(file.type));
    if (accepted.length === 0) throw new Error('none of the files is a JPEG, PNG or WebP image');

    let uploaded = 0;
    const mediaKeys = await lastValueFrom(
      from(accepted).pipe(
        concatMap(async (file) => {
          const slot = await this.upload(file);
          onProgress?.(++uploaded);
          return slot.mediaKey;
        }),
        toArray(),
      ),
    );
    const enrollment = await lastValueFrom(this.http.post<Enrollment>(`${this.enrollmentUrl(entityName, objectId)}/photos`, { mediaKeys }));
    return normalize(enrollment);
  }

  deletePhoto(entityName: string, objectId: string, photoId: string): Observable<void> {
    return this.http.delete<void>(`${this.enrollmentUrl(entityName, objectId)}/photos/${encodeURIComponent(photoId)}`);
  }

  deleteAll(entityName: string, objectId: string): Observable<void> {
    return this.http.delete<void>(this.enrollmentUrl(entityName, objectId));
  }

  private async upload(file: File): Promise<MediaUploadSlot> {
    const slot = await lastValueFrom(
      this.http.post<MediaUploadSlot>(`${this.root}/media-uploads`, {
        purpose: 'ENROLLMENT_PHOTO',
        fileName: file.name,
        contentType: file.type,
        sizeBytes: file.size,
      }),
    );
    const headers = new HttpHeaders({ 'Content-Type': file.type, ...slot.requiredHeaders });
    await lastValueFrom(this.storage.put(slot.uploadUrl, file, { headers, responseType: 'text' }));
    return slot;
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
