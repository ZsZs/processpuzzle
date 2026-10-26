import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { ModuleTranslationService } from './module-translation.service';

describe('ModuleTranslationService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: ModuleTranslationService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: serviceRoot } } }],
    });
    service = TestBed.inject(ModuleTranslationService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('reads the coverage of the module draft', async () => {
    const pending = firstValueFrom(service.coverage('claims'));

    const request = controller.expectOne(`${serviceRoot}/modules/claims/translation-coverage`);
    expect(request.request.method).toBe('GET');
    request.flush([{ locale: 'de', defaultLocale: false, keyCount: 3, translatedCount: 1, missingKeys: ['a'] }]);

    expect(await pending).toHaveLength(1);
  });

  it('downloads the prepared files as a blob', async () => {
    const pending = firstValueFrom(service.download('claims'));

    const request = controller.expectOne(`${serviceRoot}/modules/claims/translations`);
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['zip']));

    expect((await pending).size).toBe(3);
  });

  // Multipart, so no Content-Type of ours: the browser has to add the boundary.
  it('uploads the translated file as multipart, leaving the content type to the browser', async () => {
    const pending = firstValueFrom(service.upload('claims', new File(['{}'], 'de.json')));

    const request = controller.expectOne(`${serviceRoot}/modules/claims/translations`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toBeInstanceOf(FormData);
    expect((request.request.body as FormData).get('file')).toBeInstanceOf(File);
    expect(request.request.headers.has('Content-Type')).toBe(false);
    request.flush({ languages: [] });

    expect(await pending).toEqual({ languages: [] });
  });

  it('reads the bundle of a channel and language', async () => {
    const pending = firstValueFrom(service.bundle('claims', 'de', 'released'));

    const request = controller.expectOne((candidate) => candidate.url === `${serviceRoot}/modules/claims/translations/de`);
    expect(request.request.params.get('revision')).toBe('released');
    request.flush({ 'module.name': 'Schäden' });

    expect(await pending).toEqual({ 'module.name': 'Schäden' });
  });

  it('encodes what it puts in the path', async () => {
    const pending = firstValueFrom(service.bundle('claims', 'pt BR', 'draft'));

    controller.expectOne((candidate) => candidate.url === `${serviceRoot}/modules/claims/translations/pt%20BR`).flush({ ok: 'yes' });

    expect(await pending).toEqual({ ok: 'yes' });
  });
});
