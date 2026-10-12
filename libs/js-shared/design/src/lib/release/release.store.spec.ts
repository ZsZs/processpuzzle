import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PendingRelease, Release } from './release';
import { ReleaseService } from './release.service';
import { ReleaseStore } from './release.store';

const release: Release = { releaseNumber: 1, releasedAt: '2026-10-11T10:00:00Z', items: [{ kind: 'app', key: 'app', change: 'added' }] };

function pending(problems: PendingRelease['problems'] = []): PendingRelease {
  return {
    changes: [
      { kind: 'module', key: 'claims', change: 'changed' },
      { kind: 'module', key: 'orders', change: 'removed' },
      { kind: 'app', key: 'app', change: 'changed' },
    ],
    problems,
  };
}

const warning = { severity: 'warning' as const, kind: 'app', key: 'app', errorId: 'release.unresolved-reference', message: 'Mounts a missing module.' };
const error = { ...warning, severity: 'error' as const };

describe('ReleaseStore', () => {
  const service = { pending: vi.fn(), publish: vi.fn(), history: vi.fn() };

  function store(): InstanceType<typeof ReleaseStore> {
    TestBed.configureTestingModule({ providers: [ReleaseStore, { provide: ReleaseService, useValue: service }] });
    return TestBed.inject(ReleaseStore);
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    vi.resetAllMocks();
    service.pending.mockReturnValue(of(pending()));
    service.history.mockReturnValue(of([]));
  });

  it('groups the pending changes by kind, in release order', async () => {
    const releaseStore = store();
    await releaseStore.load();

    expect(releaseStore.changesByKind().map((group) => [group.kind, group.changes.length])).toEqual([
      ['module', 2],
      ['app', 1],
    ]);
    expect(releaseStore.canPublish()).toBe(true);
  });

  it('cannot publish while nothing is pending', async () => {
    service.pending.mockReturnValue(of({ changes: [], problems: [] }));
    const releaseStore = store();
    await releaseStore.load();

    expect(releaseStore.canPublish()).toBe(false);
  });

  it('cannot publish over an error', async () => {
    service.pending.mockReturnValue(of(pending([error])));
    const releaseStore = store();
    await releaseStore.load();
    releaseStore.acknowledgeWarnings(true);

    expect(releaseStore.canPublish()).toBe(false);
  });

  it('publishes over warnings only once they are confirmed, and says so to the server', async () => {
    service.pending.mockReturnValue(of(pending([warning])));
    service.publish.mockReturnValue(of({ released: release }));
    const releaseStore = store();
    await releaseStore.load();

    expect(releaseStore.canPublish()).toBe(false);
    releaseStore.acknowledgeWarnings(true);
    await releaseStore.publish();

    expect(service.publish).toHaveBeenCalledWith(true);
  });

  it('reloads after a release and confirms it', async () => {
    service.publish.mockReturnValue(of({ released: release }));
    const releaseStore = store();
    await releaseStore.load();
    service.pending.mockReturnValue(of({ changes: [], problems: [] }));
    service.history.mockReturnValue(of([release]));

    await releaseStore.publish();

    expect(releaseStore.released()).toEqual(release);
    expect(releaseStore.history()).toEqual([release]);
    expect(releaseStore.changesByKind()).toEqual([]);
  });

  it('shows what refused a release in place of what it was looking at', async () => {
    service.publish.mockReturnValue(of({ refused: pending([error]) }));
    const releaseStore = store();
    await releaseStore.load();

    await releaseStore.publish();

    expect(releaseStore.errors()).toEqual([error]);
    expect(releaseStore.released()).toBeUndefined();
  });

  it('reports a failure without a pending state as an error', async () => {
    service.pending.mockReturnValue(throwError(() => new Error('offline')));
    const releaseStore = store();

    await releaseStore.load();

    expect(releaseStore.error()).toBeDefined();
    expect(releaseStore.isBusy()).toBe(false);
  });
});
