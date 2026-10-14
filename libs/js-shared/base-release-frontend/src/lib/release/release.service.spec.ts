import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { MigrationStep, PendingRelease, Release } from './release';
import { ReleaseService } from './release.service';

const ROOT = 'http://backend/organizations/acme';

const refused: PendingRelease = {
  changes: [{ kind: 'app', key: 'app', change: 'changed' }],
  problems: [{ severity: 'error', kind: 'app', key: 'app', errorId: 'release.unresolved-reference', message: 'Missing entity.' }],
};

describe('ReleaseService', () => {
  let service: ReleaseService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { BACKEND_SERVICE_ROOT: ROOT } } }],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(ReleaseService);
  });

  afterEach(() => http.verify());

  it('reads the dry run under the organization root', async () => {
    const result = firstValueFrom(service.pending());
    http.expectOne(`${ROOT}/releases/pending`).flush({ changes: [], problems: [] });

    await expect(result).resolves.toEqual({ changes: [], problems: [] });
  });

  it('publishes with the confirmation of warnings in the body', async () => {
    const release: Release = { releaseNumber: 3, releasedAt: '2026-10-11T10:00:00Z', items: [] };

    const result = firstValueFrom(service.publish(true));
    const request = http.expectOne(`${ROOT}/releases`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ acknowledgeWarnings: true });
    request.flush(release, { status: 201, statusText: 'Created' });

    await expect(result).resolves.toEqual({ released: release });
  });

  it('answers a refusal with the pending state that refused it, not with an error', async () => {
    const result = firstValueFrom(service.publish(false));
    http.expectOne(`${ROOT}/releases`).flush(refused, { status: 409, statusText: 'Conflict' });

    await expect(result).resolves.toEqual({ refused });
  });

  it('errors on a failure that carries no pending state', async () => {
    const result = firstValueFrom(service.publish(false));
    http.expectOne(`${ROOT}/releases`).flush(null, { status: 403, statusText: 'Forbidden' });

    await expect(result).rejects.toBeDefined();
  });

  it('reads and replaces the pending migration steps', async () => {
    const step: MigrationStep = { kind: 'entity', key: 'customer', operation: 'renameAttribute', subject: 'customerName', parameters: { to: 'name' } };

    const listed = firstValueFrom(service.pendingSteps());
    http.expectOne(`${ROOT}/releases/pending/steps`).flush([]);
    await expect(listed).resolves.toEqual([]);

    const replaced = firstValueFrom(service.replacePendingSteps([step]));
    const request = http.expectOne(`${ROOT}/releases/pending/steps`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual([step]);
    request.flush([{ ...step, id: 's-1' }]);

    await expect(replaced).resolves.toEqual([{ ...step, id: 's-1' }]);
  });

  it('reads the history', async () => {
    const result = firstValueFrom(service.history());
    http.expectOne(`${ROOT}/releases`).flush([]);

    await expect(result).resolves.toEqual([]);
  });
});
