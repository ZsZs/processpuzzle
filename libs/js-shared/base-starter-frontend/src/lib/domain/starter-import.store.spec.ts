import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogStarter, ImportReport, InstalledStarter } from './starter';
import { StarterImportStore } from './starter-import.store';
import { StarterService } from './starter.service';

const wouldApply: ImportReport = { dryRun: true, status: 'would-apply', items: [{ kind: 'entity', key: 'item', action: 'create' }] };
const applied: ImportReport = { ...wouldApply, dryRun: false, status: 'applied' };
const rejected: ImportReport = { dryRun: true, status: 'rejected', errors: [{ message: 'it holds 3 entity objects' }] };
const installed: InstalledStarter[] = [{ starterId: 'inventory', version: '1.0.0', installedAt: '2026-10-05T10:00:00Z' }];
const catalog: CatalogStarter[] = [
  {
    id: 'inventory',
    name: 'Inventory',
    versions: [
      { version: '1.1.0', status: 'published' },
      { version: '1.0.0', status: 'deprecated' },
    ],
  },
  { id: 'sail', name: 'Sail Race Organizer', versions: [{ version: '2.0.0', status: 'published' }] },
];

describe('StarterImportStore', () => {
  let service: { install: ReturnType<typeof vi.fn>; listInstalled: ReturnType<typeof vi.fn>; listCatalog: ReturnType<typeof vi.fn> };
  let store: InstanceType<typeof StarterImportStore>;

  beforeEach(async () => {
    service = { install: vi.fn(), listInstalled: vi.fn(() => of(installed)), listCatalog: vi.fn(() => of(catalog)) };
    TestBed.configureTestingModule({ providers: [StarterImportStore, { provide: StarterService, useValue: service }] });
    store = TestBed.inject(StarterImportStore);
    await store.loadCatalog();
  });

  it('cannot preview or apply before a starter is picked', () => {
    expect(store.catalog()).toEqual(catalog);
    expect(store.canPreview()).toBe(false);
    expect(store.canApply()).toBe(false);
  });

  it('selects the newest version unless told otherwise', () => {
    store.select('inventory');
    expect(store.selection()).toEqual({ starterId: 'inventory', version: '1.1.0' });
    expect(store.selectedStarter()?.name).toBe('Inventory');

    store.select('inventory', '1.0.0');
    expect(store.selection()).toEqual({ starterId: 'inventory', version: '1.0.0' });

    store.select('unknown');
    expect(store.selection()).toBeUndefined();
  });

  it('enables Install only after a dry run of the current selection would apply', async () => {
    store.select('inventory');
    expect(store.canPreview()).toBe(true);
    expect(store.canApply()).toBe(false);

    service.install.mockReturnValue(of(wouldApply));
    await store.preview();

    expect(service.install).toHaveBeenCalledWith({ starterId: 'inventory', version: '1.1.0' }, true);
    expect(store.report()).toEqual(wouldApply);
    expect(store.canApply()).toBe(true);
  });

  it('keeps Install disabled after a rejected preview', async () => {
    store.select('inventory');
    service.install.mockReturnValue(of(rejected));
    await store.preview();

    expect(store.report()).toEqual(rejected);
    expect(store.canApply()).toBe(false);
  });

  it('installs, shows the applied report and reloads the installed starters', async () => {
    store.select('inventory');
    service.install.mockReturnValueOnce(of(wouldApply)).mockReturnValueOnce(of(applied));
    await store.preview();
    await store.apply();

    expect(service.install).toHaveBeenLastCalledWith({ starterId: 'inventory', version: '1.1.0' }, false);
    expect(store.report()).toEqual(applied);
    expect(store.installed()).toEqual(installed);
    expect(store.canApply()).toBe(false);
  });

  it('picking another starter or version discards the reports about the previous one', async () => {
    store.select('inventory');
    service.install.mockReturnValue(of(wouldApply));
    await store.preview();

    store.select('inventory', '1.0.0');
    expect(store.report()).toBeUndefined();

    await store.preview();
    store.select('sail');
    expect(store.report()).toBeUndefined();
    expect(store.canApply()).toBe(false);
  });

  it('reports a failure that carries no report as an error', async () => {
    store.select('inventory');
    service.install.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable', error: { errorText: 'registry down' } })));
    await store.preview();

    expect(store.error()).toBeTruthy();
    expect(store.report()).toBeUndefined();
    expect(store.isBusy()).toBe(false);
  });

  it('reports a catalog that cannot be loaded as an error', async () => {
    service.listCatalog.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable' })));
    await store.loadCatalog();

    expect(store.error()).toBeTruthy();
  });

  it('loads the installed starters', async () => {
    await store.loadInstalled();

    expect(store.installed()).toEqual(installed);
  });
});
