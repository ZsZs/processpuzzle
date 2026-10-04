import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { afterEach, describe, expect, it } from 'vitest';
import { RecognitionProfileService } from './recognition-profile.service';

describe('RecognitionProfileService', () => {
  const root = 'http://backend/organizations/testbed';
  let http: HttpTestingController;

  afterEach(() => http.verify());

  it.each([{ AI_SERVICE_ROOT: root, BACKEND_SERVICE_ROOT: 'http://unused' }, { BACKEND_SERVICE_ROOT: root }])('uses the configured AI root or backend fallback (%j)', async (configuration) => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: configuration } }],
    });
    const service = TestBed.inject(RecognitionProfileService);
    http = TestBed.inject(HttpTestingController);
    const pending = firstValueFrom(service.findAll());
    const request = http.expectOne(`${root}/recognition-profiles`);
    expect(request.request.method).toBe('GET');
    request.flush([{ entityName: 'boat', matching: { sampleFps: 2 } }]);
    expect(await pending).toMatchObject([{ id: 'boat', entityName: 'boat', sampleFps: 2 }]);

    const deletion = firstValueFrom(service.delete('boat'));
    const deleted = http.expectOne(`${root}/recognition-profiles/boat`);
    expect(deleted.request.method).toBe('DELETE');
    deleted.flush(null);
    await deletion;
  });
});
