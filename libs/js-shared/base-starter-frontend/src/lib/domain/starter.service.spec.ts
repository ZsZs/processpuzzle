import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ImportReport, UpgradeReport } from './starter';
import { catalogRootOf, StarterService } from './starter.service';

const ROOT = 'https://backend/organizations/processpuzzle-testbed';
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
    http.expectOne('https://backend/starters').flush([{ id: 'inventory', name: 'Inventory', versions: [{ version: '1.0.0', status: 'published' }] }]);

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

  describe('upgrade', () => {
    const UPGRADE_URL = `${ROOT}/starters/inventory/upgrade`;
    const wouldUpgrade: UpgradeReport = { dryRun: true, status: 'would-apply', starterId: 'inventory', fromVersion: '1.0.0', toVersion: '1.1.0', items: [] };

    it('posts to the starter\'s upgrade resource, asking for the next version on offer when none is named', async () => {
      const result = firstValueFrom(service.upgrade('inventory', true));
      const request = http.expectOne(UPGRADE_URL);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ dryRun: true });
      request.flush(wouldUpgrade);

      await expect(result).resolves.toEqual(wouldUpgrade);
    });

    it('names the version it applies', async () => {
      const result = firstValueFrom(service.upgrade('inventory', false, '1.1.0'));
      const request = http.expectOne(UPGRADE_URL);
      expect(request.request.body).toEqual({ version: '1.1.0', dryRun: false });
      request.flush({ ...wouldUpgrade, dryRun: false, status: 'applied' });

      await expect(result).resolves.toMatchObject({ status: 'applied' });
    });

    it('emits the report a 422 carries instead of failing', async () => {
      const refused: UpgradeReport = { dryRun: true, status: 'rejected', errors: [{ message: 'No newer version' }] };
      const result = firstValueFrom(service.upgrade('inventory', true));
      http.expectOne(UPGRADE_URL).flush(refused, { status: 422, statusText: 'Unprocessable Entity' });

      await expect(result).resolves.toEqual(refused);
    });

    it('fails on an error without a report, and encodes the starter id', async () => {
      const result = firstValueFrom(service.upgrade('a b', true));
      http.expectOne(`${ROOT}/starters/a%20b/upgrade`).flush({ errorId: 'starter.not-found' }, { status: 404, statusText: 'Not Found' });

      await expect(result).rejects.toMatchObject({ status: 404 });
    });
  });

  describe('uninstall', () => {
    const removal: ImportReport = { dryRun: true, status: 'would-apply', items: [{ kind: 'entity', key: 'inv-item', action: 'delete' }] };

    it("deletes the starter's resource, asking for a dry run in the query", async () => {
      const result = firstValueFrom(service.uninstall('inventory', true));
      const request = http.expectOne(`${ROOT}/starters/inventory?dryRun=true`);
      expect(request.request.method).toBe('DELETE');
      request.flush(removal);

      await expect(result).resolves.toEqual(removal);
    });

    it('removes for real when it is not a dry run', async () => {
      const result = firstValueFrom(service.uninstall('inventory', false));
      const request = http.expectOne(`${ROOT}/starters/inventory?dryRun=false`);
      expect(request.request.method).toBe('DELETE');
      request.flush({ ...removal, dryRun: false, status: 'applied' });

      await expect(result).resolves.toMatchObject({ status: 'applied' });
    });

    it('emits the report a 409 or a 422 carries instead of failing', async () => {
      const refused: ImportReport = { dryRun: true, status: 'rejected', errors: [{ message: 'The starter\'s definitions still have data: 4 entity objects.' }] };
      const result = firstValueFrom(service.uninstall('inventory', true));
      http.expectOne(`${ROOT}/starters/inventory?dryRun=true`).flush(refused, { status: 409, statusText: 'Conflict' });

      await expect(result).resolves.toEqual(refused);
    });

    it('fails on an error without a report, and encodes the starter id', async () => {
      const result = firstValueFrom(service.uninstall('a b', true));
      http.expectOne(`${ROOT}/starters/a%20b?dryRun=true`).flush({ message: 'denied' }, { status: 403, statusText: 'Forbidden' });

      await expect(result).rejects.toBeDefined();
    });
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
        { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT, STARTER_SERVICE_ROOT: 'https://starters/organizations/acme' } } },
      ],
    });
    const own = TestBed.inject(HttpTestingController);
    const result = firstValueFrom(TestBed.inject(StarterService).listInstalled());
    own.expectOne('https://starters/organizations/acme/starters').flush([]);

    await expect(result).resolves.toEqual([]);
    own.verify();
  });
});

describe('catalogRootOf', () => {
  it.each([
    ['https://localhost:8080/organizations/acme', 'https://localhost:8080'],
    ['/api/organizations/acme/', '/api'],
    ['/api', '/api'],
  ])('%s → %s', (root, expected) => {
    expect(catalogRootOf(root)).toBe(expected);
  });
});
