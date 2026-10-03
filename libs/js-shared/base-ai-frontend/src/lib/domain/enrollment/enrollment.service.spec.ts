import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { EnrollmentService } from './enrollment.service';

const ROOT = 'http://backend/organizations/processpuzzle-testbed';

describe('EnrollmentService', () => {
  let service: EnrollmentService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        // An interceptor that would break a presigned URL: the storage PUT must never pass through it.
        provideHttpClient(withInterceptors([(request, next) => next(request.clone({ setHeaders: { Authorization: 'Bearer user-token' } }))])),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(EnrollmentService);
  });

  afterEach(() => http.verify());

  it('answers undefined, not an error, when the entity type has no profile', async () => {
    const result = firstValueFrom(service.find('dog', 'o-1'));
    http.expectOne(`${ROOT}/entities/dog/o-1/enrollment`).flush({ errorId: 'ai.profile.not-found' }, { status: 404, statusText: 'Not Found' });
    expect(await result).toBeUndefined();
  });

  it('uploads each photo straight to its presigned URL, then enrolls the media keys', async () => {
    const photo = new File(['jpeg'], 'boat.jpg', { type: 'image/jpeg' });
    const ignored = new File(['text'], 'notes.txt', { type: 'text/plain' });
    const progress: number[] = [];

    const done = service.addPhotos('boat', 'o-1', [photo, ignored], (n) => progress.push(n));

    const slot = await waitFor(() => http.match(`${ROOT}/media-uploads`)[0]);
    expect(slot.request.body).toEqual({ purpose: 'ENROLLMENT_PHOTO', fileName: 'boat.jpg', contentType: 'image/jpeg', sizeBytes: 4 });
    // The user's token reached the backend call, as it should…
    expect(slot.request.headers.get('Authorization')).toBe('Bearer user-token');
    slot.flush({ mediaKey: 'm-1', uploadUrl: 'http://minio/put?sig=x', requiredHeaders: { 'Content-Type': 'image/jpeg' }, expiresAt: '' });

    // …but not the PUT to storage, which goes straight to HttpBackend, past every interceptor.
    const put = await waitFor(() => http.match('http://minio/put?sig=x')[0]);
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.has('Authorization')).toBe(false);
    expect(put.request.headers.get('Content-Type')).toBe('image/jpeg');
    put.flush('');

    const enroll = await waitFor(() => http.match(`${ROOT}/entities/boat/o-1/enrollment/photos`)[0]);
    expect(enroll.request.body).toEqual({ mediaKeys: ['m-1'] });
    enroll.flush({ entityName: 'boat', objectId: 'o-1', status: 'PROCESSING', photos: [{ photoId: 'p-1', status: 'PENDING', addedAt: '' }] });

    const enrollment = await done;
    expect(enrollment.photos[0].identifierMismatch).toBe(false);
    expect(progress).toEqual([1]);
  });

  it('refuses a batch with no image in it before reserving anything', async () => {
    await expect(service.addPhotos('boat', 'o-1', [new File(['x'], 'a.txt', { type: 'text/plain' })])).rejects.toThrow(/JPEG/);
  });
});

async function waitFor<T>(probe: () => T | undefined): Promise<T> {
  for (let i = 0; i < 50; i++) {
    const found = probe();
    if (found) return found;
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
  throw new Error('request never sent');
}
