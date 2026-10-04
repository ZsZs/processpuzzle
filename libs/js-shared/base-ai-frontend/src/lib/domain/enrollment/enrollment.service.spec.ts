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
    http.expectNone(`${ROOT}/media-uploads`);
  });

  it('normalizes missing photos and preserves explicit mismatch flags', async () => {
    const empty = firstValueFrom(service.find('boat', 'o-1'));
    http.expectOne(`${ROOT}/entities/boat/o-1/enrollment`).flush({ status: 'NOT_ENROLLED' });
    expect((await empty)?.photos).toEqual([]);

    const populated = firstValueFrom(service.find('boat', 'o-1'));
    http.expectOne(`${ROOT}/entities/boat/o-1/enrollment`).flush({
      photos: [{ identifierMismatch: true }, { identifierMismatch: false }, {}],
    });
    expect((await populated)?.photos.map((photo) => photo.identifierMismatch)).toEqual([true, false, false]);
  });

  it('propagates backend errors other than a missing profile', async () => {
    const result = firstValueFrom(service.find('boat', 'o-1'));
    const rejected = expect(result).rejects.toMatchObject({ status: 503 });
    http.expectOne(`${ROOT}/entities/boat/o-1/enrollment`).flush('unavailable', { status: 503, statusText: 'Service Unavailable' });
    await rejected;
  });

  it('encodes each URL segment when reading or deleting photos and galleries', async () => {
    const url = `${ROOT}/entities/sail%20boat/object%2F1/enrollment`;
    const read = firstValueFrom(service.find('sail boat', 'object/1'));
    http.expectOne(url).flush({ photos: [] });
    await read;

    const deletedPhoto = firstValueFrom(service.deletePhoto('sail boat', 'object/1', 'photo/1'));
    const photoRequest = http.expectOne(`${url}/photos/photo%2F1`);
    expect(photoRequest.request.method).toBe('DELETE');
    photoRequest.flush(null);
    await deletedPhoto;

    const deletedGallery = firstValueFrom(service.deleteAll('sail boat', 'object/1'));
    const galleryRequest = http.expectOne(url);
    expect(galleryRequest.request.method).toBe('DELETE');
    galleryRequest.flush(null);
    await deletedGallery;
  });

  it('uploads multiple images sequentially without requiring a progress callback or slot headers', async () => {
    const files = [new File(['png'], 'one.png', { type: 'image/png' }), new File(['webp'], 'two.webp', { type: 'image/webp' })];
    const done = service.addPhotos('boat', 'o-1', files);

    for (const [index, file] of files.entries()) {
      const slot = await waitFor(() => http.match(`${ROOT}/media-uploads`)[0]);
      expect(slot.request.body.fileName).toBe(file.name);
      slot.flush({ mediaKey: `m-${index}`, uploadUrl: `http://minio/${index}` });
      const put = await waitFor(() => http.match(`http://minio/${index}`)[0]);
      expect(put.request.body).toBe(file);
      expect(put.request.headers.get('Content-Type')).toBe(file.type);
      http.expectNone(`${ROOT}/media-uploads`);
      http.expectNone(`${ROOT}/entities/boat/o-1/enrollment/photos`);
      put.flush('');
    }

    const enroll = await waitFor(() => http.match(`${ROOT}/entities/boat/o-1/enrollment/photos`)[0]);
    expect(enroll.request.body).toEqual({ mediaKeys: ['m-0', 'm-1'] });
    enroll.flush({ status: 'PROCESSING' });
    expect((await done).photos).toEqual([]);
  });

  it('stops the batch and propagates a storage failure without enrolling incomplete uploads', async () => {
    const photo = new File(['jpeg'], 'boat.jpg', { type: 'image/jpeg' });
    const progress: number[] = [];
    const done = service.addPhotos('boat', 'o-1', [photo, photo], (count) => progress.push(count));
    const rejected = expect(done).rejects.toMatchObject({ status: 403 });
    const slot = await waitFor(() => http.match(`${ROOT}/media-uploads`)[0]);
    slot.flush({ mediaKey: 'm-1', uploadUrl: 'http://minio/denied', requiredHeaders: { 'x-amz-meta-purpose': 'enrollment' } });
    const put = await waitFor(() => http.match('http://minio/denied')[0]);
    expect(put.request.headers.get('x-amz-meta-purpose')).toBe('enrollment');
    put.flush('expired', { status: 403, statusText: 'Forbidden' });

    await rejected;
    expect(progress).toEqual([]);
    http.expectNone(`${ROOT}/media-uploads`);
    http.expectNone(`${ROOT}/entities/boat/o-1/enrollment/photos`);
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
