import { HttpBackend, HttpClient, HttpHeaders } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { concatMap, from, lastValueFrom, map, Observable, toArray } from 'rxjs';
import { MediaUploadSlot, Recognition, RECOGNITION_FRAME_TYPES, RECOGNITION_MAX_FRAMES } from './recognition';

/** How often a QUEUED recognition is re-read. */
export const RECOGNITION_POLL_MS = 1000;

/** Re-reads before {@link RecognitionService.recognize} gives up — two minutes, far beyond a CPU shot's seconds. */
export const RECOGNITION_MAX_POLLS = 120;

/**
 * Asks base-ai which of the candidates is in front of the camera.
 *
 * **Frames go straight to object storage.** For each frame, `createMediaUpload` reserves a slot and answers
 * a presigned PUT URL; the frame is PUT there; then the slots' media keys are handed to `startRecognition`.
 * The PUT is sent through a bare `HttpClient` on `HttpBackend`, past every interceptor: the URL is signed,
 * and a header an interceptor added — an `Authorization`, say — would invalidate the signature.
 */
@Injectable({ providedIn: 'root' })
export class RecognitionService {
  private readonly http = inject(HttpClient);
  private readonly storage = new HttpClient(inject(HttpBackend));
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'AI_SERVICE_ROOT');

  /**
   * Uploads the frames, starts the recognition and polls it until the vision server has answered.
   *
   * @param entityName        the profile's key, the entity definition code — e.g. `boat`
   * @param frames            one to five shots of the same subject
   * @param candidateObjectIds the objects that may be in front of the camera
   * @param signal             abandons the recognition: no further upload or poll, and the promise rejects
   *                           with an `AbortError`. The job itself runs to its end on the server and is purged
   *                           with the other recognitions; abandoning it costs nothing but its CPU seconds.
   */
  async recognize(entityName: string, frames: readonly Blob[], candidateObjectIds: readonly string[], signal?: AbortSignal): Promise<Recognition> {
    let recognition = await this.start(entityName, frames, candidateObjectIds, signal);
    for (let polls = 0; recognition.status === 'QUEUED'; polls++) {
      if (polls >= RECOGNITION_MAX_POLLS) throw new Error('the recognition did not finish in time');
      await delay(RECOGNITION_POLL_MS, signal);
      recognition = await lastValueFrom(this.find(recognition.recognitionId));
      signal?.throwIfAborted();
    }
    return recognition;
  }

  /** Uploads the frames one after another and starts the recognition; it answers QUEUED. */
  async start(entityName: string, frames: readonly Blob[], candidateObjectIds: readonly string[], signal?: AbortSignal): Promise<Recognition> {
    const accepted = frames.filter((frame) => RECOGNITION_FRAME_TYPES.includes(frame.type)).slice(0, RECOGNITION_MAX_FRAMES);
    if (accepted.length === 0) throw new Error('none of the frames is a JPEG, PNG or WebP image');
    if (candidateObjectIds.length === 0) throw new Error('there is no candidate to recognize');

    const mediaKeys = await lastValueFrom(
      from(accepted).pipe(
        concatMap(async (frame, index) => {
          signal?.throwIfAborted();
          return (await this.upload(frame, index)).mediaKey;
        }),
        toArray(),
      ),
    );
    signal?.throwIfAborted();
    const recognition = await lastValueFrom(
      this.http.post<Recognition>(`${this.root}/recognitions`, { entityName, mediaKeys, candidateObjectIds: [...new Set(candidateObjectIds)] }),
    );
    signal?.throwIfAborted();
    return normalize(recognition);
  }

  find(recognitionId: string): Observable<Recognition> {
    return this.http.get<Recognition>(`${this.root}/recognitions/${encodeURIComponent(recognitionId)}`).pipe(map(normalize));
  }

  private async upload(frame: Blob, index: number): Promise<MediaUploadSlot> {
    const slot = await lastValueFrom(
      this.http.post<MediaUploadSlot>(`${this.root}/media-uploads`, {
        purpose: 'RECOGNITION_FRAME',
        fileName: frame instanceof File ? frame.name : `frame-${index + 1}.${frame.type.split('/')[1] ?? 'jpg'}`,
        contentType: frame.type,
        sizeBytes: frame.size,
      }),
    );
    const headers = new HttpHeaders({ 'Content-Type': frame.type, ...slot.requiredHeaders });
    await lastValueFrom(this.storage.put(slot.uploadUrl, frame, { headers, responseType: 'text' }));
    return slot;
  }
}

function normalize(recognition: Recognition): Recognition {
  return { ...recognition, candidates: recognition?.candidates ?? [], candidatesWithoutGallery: recognition?.candidatesWithoutGallery ?? [] };
}

/** A pause that an abort cuts short, rejecting with the signal's reason. */
function delay(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason);
      return;
    }
    const timer = setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    const onAbort = () => {
      clearTimeout(timer);
      reject(signal?.reason);
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}
