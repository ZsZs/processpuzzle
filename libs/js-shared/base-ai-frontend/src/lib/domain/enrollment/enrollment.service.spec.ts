import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
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
        provideHttpClient(),
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

  it('synchronizes by POSTing an empty body to the enrollment and normalizes the answer', async () => {
    const result = firstValueFrom(service.synchronize('boat', 'o-1'));
    const request = http.expectOne(`${ROOT}/entities/boat/o-1/enrollment`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    request.flush({
      entityName: 'boat',
      objectId: 'o-1',
      status: 'PROCESSING',
      photos: [{ photoId: 'p-1', photoRef: 'ref-1', status: 'PENDING', addedAt: '' }],
    });

    const enrollment = await result;
    expect(enrollment.status).toBe('PROCESSING');
    expect(enrollment.photos[0]).toMatchObject({ photoRef: 'ref-1', identifierMismatch: false });
  });

  it('normalizes a synchronization answer without photos', async () => {
    const result = firstValueFrom(service.synchronize('boat', 'o-1'));
    http.expectOne(`${ROOT}/entities/boat/o-1/enrollment`).flush({ status: 'NOT_ENROLLED' });
    expect((await result).photos).toEqual([]);
  });

  it('propagates a synchronization failure, even a 404', async () => {
    const result = firstValueFrom(service.synchronize('boat', 'o-1'));
    const rejected = expect(result).rejects.toMatchObject({ status: 404 });
    http.expectOne(`${ROOT}/entities/boat/o-1/enrollment`).flush('no profile', { status: 404, statusText: 'Not Found' });
    await rejected;
  });

  it('discards the gallery with a DELETE on the enrollment', async () => {
    const result = firstValueFrom(service.deleteAll('boat', 'o-1'), { defaultValue: undefined });
    const request = http.expectOne(`${ROOT}/entities/boat/o-1/enrollment`);
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    await result;
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

  it('encodes each URL segment when reading, synchronizing or deleting a gallery', async () => {
    const url = `${ROOT}/entities/sail%20boat/object%2F1/enrollment`;
    const read = firstValueFrom(service.find('sail boat', 'object/1'));
    http.expectOne(url).flush({ photos: [] });
    await read;

    const synchronized = firstValueFrom(service.synchronize('sail boat', 'object/1'));
    http.expectOne({ url, method: 'POST' }).flush({ photos: [] });
    await synchronized;

    const deleted = firstValueFrom(service.deleteAll('sail boat', 'object/1'), { defaultValue: undefined });
    http.expectOne({ url, method: 'DELETE' }).flush(null);
    await deleted;
  });
});
