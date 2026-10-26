import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { moduleAssetContextOf } from './module-asset-context';
import { ModuleAssetService } from './module-asset.service';

describe('ModuleAssetService', () => {
  const serviceRoot = 'http://localhost:3000/organizations/processpuzzle-testbed';
  let service: ModuleAssetService;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: serviceRoot } } }],
    });
    service = TestBed.inject(ModuleAssetService);
    controller = TestBed.inject(HttpTestingController);
  });

  it('lists the draft by default and the released assets on request', async () => {
    const draft = firstValueFrom(service.list('claims'));
    const request = controller.expectOne((candidate) => candidate.url === `${serviceRoot}/modules/claims/assets`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('revision')).toBe('draft');
    request.flush([{ path: 'a.md', mimeType: 'text/markdown', size: 1, sha256: 'abc' }]);
    expect(await draft).toHaveLength(1);

    const released = firstValueFrom(service.list('claims', 'released'));
    const second = controller.expectOne((candidate) => candidate.url === `${serviceRoot}/modules/claims/assets`);
    expect(second.request.params.get('revision')).toBe('released');
    second.flush([]);
    expect(await released).toEqual([]);
  });

  // Multipart, so no Content-Type of ours: the browser has to add the boundary.
  it('uploads one file as multipart, with the path when there is one', async () => {
    const pending = firstValueFrom(service.upload('claims', new File(['# Hi'], 'welcome.md'), 'docs/welcome.md'));

    const request = controller.expectOne(`${serviceRoot}/modules/claims/assets`);
    expect(request.request.method).toBe('POST');
    const body = request.request.body as FormData;
    expect(body.get('file')).toBeInstanceOf(File);
    expect(body.get('path')).toBe('docs/welcome.md');
    expect(request.request.headers.has('Content-Type')).toBe(false);
    request.flush({ path: 'docs/welcome.md', mimeType: 'text/markdown', size: 4, sha256: 'abc' });

    expect((await pending).path).toBe('docs/welcome.md');
  });

  it("leaves the path out when none is given, the file name then being the backend's to use", async () => {
    const pending = firstValueFrom(service.upload('claims', new File(['x'], 'a.md')));

    const request = controller.expectOne(`${serviceRoot}/modules/claims/assets`);
    expect((request.request.body as FormData).has('path')).toBe(false);
    request.flush({ path: 'a.md', mimeType: 'text/markdown', size: 1, sha256: 'abc' });
    await pending;
  });

  it('removes an asset by its path', async () => {
    const pending = firstValueFrom(service.remove('claims', 'docs/a.md'));

    const request = controller.expectOne((candidate) => candidate.url === `${serviceRoot}/modules/claims/assets`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.params.get('path')).toBe('docs/a.md');
    request.flush(null);
    await pending;
  });

  it('fetches the bytes of an asset as a blob, from the channel asked for', async () => {
    const pending = firstValueFrom(service.content('claims', 'docs/a.md', 'released'));

    const request = controller.expectOne((candidate) => candidate.url === `${serviceRoot}/modules/claims/assets/content`);
    expect(request.request.responseType).toBe('blob');
    expect(request.request.params.get('path')).toBe('docs/a.md');
    expect(request.request.params.get('revision')).toBe('released');
    request.flush(new Blob(['abc']));

    expect((await pending).size).toBe(3);
  });

  it('gives the widgets of a mounted module its own files, from the channel the mount was built from', async () => {
    const context = moduleAssetContextOf(service, 'claims', 'draft');

    const pending = firstValueFrom(context.blob('img/a.png'));

    const request = controller.expectOne((candidate) => candidate.url === `${serviceRoot}/modules/claims/assets/content`);
    expect(request.request.params.get('path')).toBe('img/a.png');
    expect(request.request.params.get('revision')).toBe('draft');
    request.flush(new Blob(['x']));
    await pending;
  });
});
