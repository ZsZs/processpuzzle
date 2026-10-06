import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ImportReport } from './starter';
import { catalogRootOf, StarterService } from './starter.service';

const ROOT = 'http://backend/organizations/processpuzzle-testbed';
const INSTALL_URL = `${ROOT}/starters/install`;

const rejected: ImportReport = { dryRun: true, status: 'rejected', errors: [{ message: 'it holds 3 entity objects' }] };

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

  it('reads the catalog from the root without its organization segment', async () => {
    const result = firstValueFrom(service.listCatalog());
    http.expectOne('http://backend/starters').flush([{ id: 'inventory', name: 'Inventory', versions: [{ version: '1.0.0', status: 'published' }] }]);

    await expect(result).resolves.toEqual([expect.objectContaining({ id: 'inventory' })]);
  });

  it('installs by reference, with the dryRun flag in the body', async () => {
    const report: ImportReport = { dryRun: true, status: 'would-apply', items: [{ kind: 'entity', key: 'item', action: 'create' }] };

    const result = firstValueFrom(service.install({ starterId: 'inventory', version: '1.0.0' }, true));
    const request = http.expectOne(INSTALL_URL);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ starterId: 'inventory', version: '1.0.0', dryRun: true });
    request.flush(report);

    await expect(result).resolves.toEqual(report);
  });

  it.each([
    [409, 'Conflict'],
    [413, 'Payload Too Large'],
    [422, 'Unprocessable Entity'],
  ])('emits the report a %s carries instead of failing', async (status, statusText) => {
    const result = firstValueFrom(service.install({ starterId: 'inventory', version: '1.0.0' }, false));
    http.expectOne(INSTALL_URL).flush(rejected, { status, statusText });

    await expect(result).resolves.toEqual(rejected);
  });

  it('fails on an error without a report', async () => {
    const result = firstValueFrom(service.install({ starterId: 'inventory', version: '9.9.9' }, true));
    http.expectOne(INSTALL_URL).flush({ errorId: 'starter.not-found' }, { status: 404, statusText: 'Not Found' });

    await expect(result).rejects.toMatchObject({ status: 404 });
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

describe('catalogRootOf', () => {
  it.each([
    ['http://localhost:8080/organizations/acme', 'http://localhost:8080'],
    ['/api/organizations/acme/', '/api'],
    ['/api', '/api'],
  ])('%s → %s', (root, expected) => {
    expect(catalogRootOf(root)).toBe(expected);
  });
});
