import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ImportReport } from './starter';
import { StarterService } from './starter.service';

const ROOT = 'http://backend/organizations/processpuzzle-testbed';
const IMPORT_URL = `${ROOT}/definitions/import`;

const rejected: ImportReport = { dryRun: true, status: 'rejected', errors: [{ file: '../evil.yaml', message: 'Unsafe path' }] };

describe('StarterService', () => {
  let service: StarterService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } } }],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(StarterService);
  });

  afterEach(() => http.verify());

  it('uploads the bundle as multipart with the dryRun flag', async () => {
    const bundle = new File(['zip'], 'inventory.zip', { type: 'application/zip' });
    const report: ImportReport = { dryRun: true, status: 'would-apply', items: [{ kind: 'entity', key: 'item', action: 'create' }] };

    const result = firstValueFrom(service.importBundle(bundle, true));
    const request = http.expectOne((r) => r.url === IMPORT_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.params.get('dryRun')).toBe('true');
    const body = request.request.body as FormData;
    expect((body.get('bundle') as File).name).toBe('inventory.zip');
    request.flush(report);

    await expect(result).resolves.toEqual(report);
  });

  it.each([
    [422, 'Unprocessable Entity'],
    [413, 'Payload Too Large'],
  ])('emits the report a %s carries instead of failing', async (status, statusText) => {
    const result = firstValueFrom(service.importBundle(new Blob(['zip']), false));
    const request = http.expectOne((r) => r.url === IMPORT_URL);
    expect(request.request.params.get('dryRun')).toBe('false');
    request.flush(rejected, { status, statusText });

    await expect(result).resolves.toEqual(rejected);
  });

  it('fails on an error without a report', async () => {
    const result = firstValueFrom(service.importBundle(new Blob(['zip']), true));
    http.expectOne((r) => r.url === IMPORT_URL).flush({ errorId: 'forbidden' }, { status: 403, statusText: 'Forbidden' });

    await expect(result).rejects.toMatchObject({ status: 403 });
  });

  it('lists the installed starters', async () => {
    const result = firstValueFrom(service.listInstalled());
    http.expectOne(`${ROOT}/starters`).flush([{ starterId: 'inventory', version: '1.0.0', installedAt: '2026-10-05T10:00:00Z', customizedDefinitions: 1 }]);

    await expect(result).resolves.toEqual([expect.objectContaining({ starterId: 'inventory', customizedDefinitions: 1 })]);
  });

  it('prefers STARTER_SERVICE_ROOT when the deployment names one', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT, STARTER_SERVICE_ROOT: 'http://starters/organizations/acme' } } },
      ],
    });
    const own = TestBed.inject(HttpTestingController);
    const result = firstValueFrom(TestBed.inject(StarterService).listInstalled());
    own.expectOne('http://starters/organizations/acme/starters').flush([]);

    await expect(result).resolves.toEqual([]);
    own.verify();
  });
});
