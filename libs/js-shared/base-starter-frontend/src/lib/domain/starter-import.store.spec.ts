import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ImportReport, InstalledStarter } from './starter';
import { StarterImportStore } from './starter-import.store';
import { StarterService } from './starter.service';

const wouldApply: ImportReport = { dryRun: true, status: 'would-apply', items: [{ kind: 'entity', key: 'item', action: 'create' }] };
const applied: ImportReport = { ...wouldApply, dryRun: false, status: 'applied' };
const rejected: ImportReport = { dryRun: true, status: 'rejected', errors: [{ message: 'bad' }] };
const installed: InstalledStarter[] = [{ starterId: 'inventory', version: '1.0.0', installedAt: '2026-10-05T10:00:00Z' }];

describe('StarterImportStore', () => {
  let service: { importBundle: ReturnType<typeof vi.fn>; listInstalled: ReturnType<typeof vi.fn> };
  let store: InstanceType<typeof StarterImportStore>;
  const bundle = new File(['zip'], 'inventory.zip');

  beforeEach(() => {
    service = { importBundle: vi.fn(), listInstalled: vi.fn(() => of(installed)) };
    TestBed.configureTestingModule({ providers: [StarterImportStore, { provide: StarterService, useValue: service }] });
    store = TestBed.inject(StarterImportStore);
  });

  it('cannot preview or apply before a bundle is picked', () => {
    expect(store.canPreview()).toBe(false);
    expect(store.canApply()).toBe(false);
  });

  it('enables Import only after a dry run of the current bundle would apply', async () => {
    store.selectBundle(bundle);
    expect(store.canPreview()).toBe(true);
    expect(store.canApply()).toBe(false);

    service.importBundle.mockReturnValue(of(wouldApply));
    await store.preview();

    expect(service.importBundle).toHaveBeenCalledWith(bundle, true);
    expect(store.report()).toEqual(wouldApply);
    expect(store.canApply()).toBe(true);
  });

  it('keeps Import disabled after a rejected preview', async () => {
    store.selectBundle(bundle);
    service.importBundle.mockReturnValue(of(rejected));
    await store.preview();

    expect(store.report()).toEqual(rejected);
    expect(store.canApply()).toBe(false);
  });

  it('applies, shows the applied report and reloads the installed starters', async () => {
    store.selectBundle(bundle);
    service.importBundle.mockReturnValueOnce(of(wouldApply)).mockReturnValueOnce(of(applied));
    await store.preview();
    await store.apply();

    expect(service.importBundle).toHaveBeenLastCalledWith(bundle, false);
    expect(store.report()).toEqual(applied);
    expect(store.installed()).toEqual(installed);
    expect(store.canApply()).toBe(false);
  });

  it('picking another bundle discards the reports about the previous one', async () => {
    store.selectBundle(bundle);
    service.importBundle.mockReturnValue(of(wouldApply));
    await store.preview();

    store.selectBundle(new File(['other'], 'other.zip'));

    expect(store.report()).toBeUndefined();
    expect(store.canApply()).toBe(false);
  });

  it('reports a failure that carries no report as an error', async () => {
    store.selectBundle(bundle);
    service.importBundle.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403, statusText: 'Forbidden', error: { errorText: 'not yours' } })));
    await store.preview();

    expect(store.error()).toBeTruthy();
    expect(store.report()).toBeUndefined();
    expect(store.isBusy()).toBe(false);
  });

  it('loads the installed starters', async () => {
    await store.loadInstalled();

    expect(store.installed()).toEqual(installed);
  });
});
