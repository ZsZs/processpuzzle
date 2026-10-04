import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Recognition, RECOGNITION_MAX_FRAMES } from './recognition';
import { RECOGNITION_MAX_POLLS, RECOGNITION_POLL_MS, RecognitionService } from './recognition.service';

const ROOT = 'http://backend/organizations/processpuzzle-testbed';

function recognition(status: Recognition['status'], extra: Partial<Recognition> = {}): Partial<Recognition> {
  return { recognitionId: 'r-1', entityName: 'boat', status, createdAt: '', ...extra };
}

describe('RecognitionService', () => {
  let service: RecognitionService;
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
    service = TestBed.inject(RecognitionService);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  /** Answers the slot reservation and the storage PUT of one frame, checking both on the way. */
  async function uploadFrame(index: number, frame: Blob, fileName: string): Promise<TestRequest> {
    const slot = await waitFor(() => http.match(`${ROOT}/media-uploads`)[0]);
    expect(slot.request.body).toEqual({ purpose: 'RECOGNITION_FRAME', fileName, contentType: frame.type, sizeBytes: frame.size });
    slot.flush({ mediaKey: `m-${index}`, uploadUrl: `http://minio/put/${index}?sig=x`, expiresAt: '' });
    const put = await waitFor(() => http.match(`http://minio/put/${index}?sig=x`)[0]);
    expect(put.request.body).toBe(frame);
    put.flush('');
    return put;
  }

  it('uploads each frame straight to its presigned URL, then starts the recognition with the media keys', async () => {
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const started = service.start('boat', [frame], ['o-1', 'o-2']);

    const slot = await waitFor(() => http.match(`${ROOT}/media-uploads`)[0]);
    expect(slot.request.body).toEqual({ purpose: 'RECOGNITION_FRAME', fileName: 'shot.jpg', contentType: 'image/jpeg', sizeBytes: 4 });
    // The user's token reached the backend call, as it should…
    expect(slot.request.headers.get('Authorization')).toBe('Bearer user-token');
    slot.flush({ mediaKey: 'm-1', uploadUrl: 'http://minio/put?sig=x', requiredHeaders: { 'x-amz-meta-purpose': 'recognition' }, expiresAt: '' });

    // …but not the PUT to storage, which goes straight to HttpBackend, past every interceptor.
    const put = await waitFor(() => http.match('http://minio/put?sig=x')[0]);
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.has('Authorization')).toBe(false);
    expect(put.request.headers.get('Content-Type')).toBe('image/jpeg');
    expect(put.request.headers.get('x-amz-meta-purpose')).toBe('recognition');
    put.flush('');

    const start = await waitFor(() => http.match(`${ROOT}/recognitions`)[0]);
    expect(start.request.method).toBe('POST');
    expect(start.request.headers.get('Authorization')).toBe('Bearer user-token');
    expect(start.request.body).toEqual({ entityName: 'boat', mediaKeys: ['m-1'], candidateObjectIds: ['o-1', 'o-2'] });
    start.flush(recognition('QUEUED'));

    expect(await started).toMatchObject({ recognitionId: 'r-1', status: 'QUEUED', candidates: [], candidatesWithoutGallery: [] });
  });

  it('names a bare Blob frame by its index and type, and uploads frames one after another', async () => {
    const frames = [new Blob(['a'], { type: 'image/jpeg' }), new Blob(['bb'], { type: 'image/webp' })];
    const started = service.start('boat', frames, ['o-1']);

    await uploadFrame(0, frames[0], 'frame-1.jpeg');
    http.expectNone(`${ROOT}/recognitions`);
    await uploadFrame(1, frames[1], 'frame-2.webp');

    const start = await waitFor(() => http.match(`${ROOT}/recognitions`)[0]);
    expect(start.request.body.mediaKeys).toEqual(['m-0', 'm-1']);
    start.flush(recognition('QUEUED'));
    await started;
  });

  it('drops frames that are not JPEG, PNG or WebP, and sends at most five', async () => {
    const text = new File(['x'], 'notes.txt', { type: 'text/plain' });
    const images = Array.from({ length: RECOGNITION_MAX_FRAMES + 2 }, (_, i) => new File([`png${i}`], `f${i}.png`, { type: 'image/png' }));
    const started = service.start('boat', [text, ...images], ['o-1']);

    for (let index = 0; index < RECOGNITION_MAX_FRAMES; index++) await uploadFrame(index, images[index], `f${index}.png`);

    const start = await waitFor(() => http.match(`${ROOT}/recognitions`)[0]);
    expect(start.request.body.mediaKeys).toHaveLength(RECOGNITION_MAX_FRAMES);
    http.expectNone(`${ROOT}/media-uploads`);
    start.flush(recognition('QUEUED'));
    await started;
  });

  it('refuses a shot with no image in it before reserving anything', async () => {
    await expect(service.start('boat', [new File(['x'], 'a.txt', { type: 'text/plain' })], ['o-1'])).rejects.toThrow(/JPEG/);
    await expect(service.start('boat', [], ['o-1'])).rejects.toThrow(/JPEG/);
    http.expectNone(`${ROOT}/media-uploads`);
  });

  it('refuses an empty candidate list before reserving anything', async () => {
    await expect(service.start('boat', [new File(['x'], 'a.jpg', { type: 'image/jpeg' })], [])).rejects.toThrow(/candidate/);
    http.expectNone(`${ROOT}/media-uploads`);
  });

  it('sends each candidate once', async () => {
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const started = service.start('boat', [frame], ['o-1', 'o-2', 'o-1', 'o-2', 'o-3']);
    await uploadFrame(0, frame, 'shot.jpg');
    const start = await waitFor(() => http.match(`${ROOT}/recognitions`)[0]);
    expect(start.request.body.candidateObjectIds).toEqual(['o-1', 'o-2', 'o-3']);
    start.flush(recognition('QUEUED'));
    await started;
  });

  it('propagates a storage failure without starting a recognition', async () => {
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const started = service.start('boat', [frame, frame], ['o-1']);
    const rejected = expect(started).rejects.toMatchObject({ status: 403 });
    const slot = await waitFor(() => http.match(`${ROOT}/media-uploads`)[0]);
    slot.flush({ mediaKey: 'm-1', uploadUrl: 'http://minio/denied' });
    const put = await waitFor(() => http.match('http://minio/denied')[0]);
    put.flush('expired', { status: 403, statusText: 'Forbidden' });

    await rejected;
    http.expectNone(`${ROOT}/media-uploads`);
    http.expectNone(`${ROOT}/recognitions`);
  });

  it('reads a recognition by id, encoding it and normalizing the arrays', async () => {
    const found = firstValueFrom(service.find('r/1'));
    http.expectOne(`${ROOT}/recognitions/r%2F1`).flush(recognition('DONE', { outcome: 'NO_SUBJECT' }));
    expect(await found).toMatchObject({ status: 'DONE', candidates: [], candidatesWithoutGallery: [] });
  });

  it('polls a queued recognition until the vision server has answered', async () => {
    vi.useFakeTimers();
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const done = service.recognize('boat', [frame], ['o-1']);
    await uploadFrame(0, frame, 'shot.jpg');
    (await waitFor(() => http.match(`${ROOT}/recognitions`)[0])).flush(recognition('QUEUED'));

    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
    http.expectOne(`${ROOT}/recognitions/r-1`).flush(recognition('QUEUED'));
    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS - 1);
    http.expectNone(`${ROOT}/recognitions/r-1`);
    await vi.advanceTimersByTimeAsync(1);
    const candidates = [{ objectId: 'o-1', score: 0.9 }];
    http.expectOne(`${ROOT}/recognitions/r-1`).flush(recognition('DONE', { outcome: 'MATCHED', objectId: 'o-1', score: 0.9, candidates }));

    expect(await done).toMatchObject({ status: 'DONE', outcome: 'MATCHED', objectId: 'o-1', candidates });
  });

  it('answers at once, without polling, a recognition that is already finished', async () => {
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const done = service.recognize('boat', [frame], ['o-1']);
    await uploadFrame(0, frame, 'shot.jpg');
    (await waitFor(() => http.match(`${ROOT}/recognitions`)[0])).flush(recognition('FAILED', { failureReason: 'no server' }));
    expect(await done).toMatchObject({ status: 'FAILED', failureReason: 'no server' });
    http.expectNone(`${ROOT}/recognitions/r-1`);
  });

  it('stops polling and rejects once abandoned', async () => {
    vi.useFakeTimers();
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const controller = new AbortController();
    const done = service.recognize('boat', [frame], ['o-1'], controller.signal);
    const rejected = expect(done).rejects.toMatchObject({ name: 'AbortError' });
    await uploadFrame(0, frame, 'shot.jpg');
    (await waitFor(() => http.match(`${ROOT}/recognitions`)[0])).flush(recognition('QUEUED'));

    controller.abort();
    await rejected;
    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS * 3);
    http.expectNone(`${ROOT}/recognitions/r-1`);
  });

  it('starts nothing when abandoned before the first upload', async () => {
    const controller = new AbortController();
    controller.abort();
    await expect(service.recognize('boat', [new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' })], ['o-1'], controller.signal)).rejects.toMatchObject({
      name: 'AbortError',
    });
    http.expectNone(`${ROOT}/media-uploads`);
  });

  it('rejects an answer received after cancellation without polling again', async () => {
    vi.useFakeTimers();
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const controller = new AbortController();
    const done = service.recognize('boat', [frame], ['o-1'], controller.signal);
    const rejected = expect(done).rejects.toMatchObject({ name: 'AbortError' });
    await uploadFrame(0, frame, 'shot.jpg');
    (await waitFor(() => http.match(`${ROOT}/recognitions`)[0])).flush(recognition('QUEUED'));

    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
    const poll = http.expectOne(`${ROOT}/recognitions/r-1`);
    controller.abort();
    poll.flush(recognition('DONE', { outcome: 'NO_SUBJECT' }));

    await rejected;
    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
    http.expectNone(`${ROOT}/recognitions/r-1`);
  });

  it('propagates a polling failure without polling again', async () => {
    vi.useFakeTimers();
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const done = service.recognize('boat', [frame], ['o-1']);
    const rejected = expect(done).rejects.toMatchObject({ status: 503 });
    await uploadFrame(0, frame, 'shot.jpg');
    (await waitFor(() => http.match(`${ROOT}/recognitions`)[0])).flush(recognition('QUEUED'));

    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
    http.expectOne(`${ROOT}/recognitions/r-1`).flush('unavailable', { status: 503, statusText: 'Service Unavailable' });

    await rejected;
    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
    http.expectNone(`${ROOT}/recognitions/r-1`);
  });

  it('accepts a recognition completed on the last allowed poll', async () => {
    vi.useFakeTimers();
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const done = service.recognize('boat', [frame], ['o-1']);
    await uploadFrame(0, frame, 'shot.jpg');
    (await waitFor(() => http.match(`${ROOT}/recognitions`)[0])).flush(recognition('QUEUED'));

    for (let poll = 0; poll < RECOGNITION_MAX_POLLS; poll++) {
      await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
      http.expectOne(`${ROOT}/recognitions/r-1`).flush(recognition(poll === RECOGNITION_MAX_POLLS - 1 ? 'DONE' : 'QUEUED'));
    }

    expect(await done).toMatchObject({ status: 'DONE' });
    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
    http.expectNone(`${ROOT}/recognitions/r-1`);
  });

  it('gives up after the maximum number of polls', async () => {
    vi.useFakeTimers();
    const frame = new File(['jpeg'], 'shot.jpg', { type: 'image/jpeg' });
    const done = service.recognize('boat', [frame], ['o-1']);
    const rejected = expect(done).rejects.toThrow(/did not finish/);
    await uploadFrame(0, frame, 'shot.jpg');
    (await waitFor(() => http.match(`${ROOT}/recognitions`)[0])).flush(recognition('QUEUED'));

    for (let poll = 0; poll < RECOGNITION_MAX_POLLS; poll++) {
      await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
      http.expectOne(`${ROOT}/recognitions/r-1`).flush(recognition('QUEUED'));
    }
    await rejected;
    await vi.advanceTimersByTimeAsync(RECOGNITION_POLL_MS);
    http.expectNone(`${ROOT}/recognitions/r-1`);
  });
});

/** Lets the service's promise chain advance until the request shows up; works under fake timers too. */
async function waitFor<T>(probe: () => T | undefined): Promise<T> {
  for (let i = 0; i < 50; i++) {
    const found = probe();
    if (found) return found;
    await Promise.resolve();
    await new Promise<void>((resolve) => queueMicrotask(resolve));
  }
  throw new Error('request never sent');
}
