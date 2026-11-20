import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogStarter, ImportReport, InstalledStarter, UpgradeReport } from './starter';
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
  let service: {
    upgrade: ReturnType<typeof vi.fn>;
    uninstall: ReturnType<typeof vi.fn>;
    install: ReturnType<typeof vi.fn>;
    listInstalled: ReturnType<typeof vi.fn>;
    listCatalog: ReturnType<typeof vi.fn>;
  };
  let store: InstanceType<typeof StarterImportStore>;

  beforeEach(async () => {
    service = { upgrade: vi.fn(), uninstall: vi.fn(), install: vi.fn(), listInstalled: vi.fn(() => of(installed)), listCatalog: vi.fn(() => of(catalog)) };
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

  describe('with the starter installed', () => {
    beforeEach(async () => {
      await store.loadInstalled();
    });

    it('lists no version newer than the installed one while none is on offer, and selects the installed one', () => {
      store.select('inventory');
      expect(store.selectableVersions().map((v) => v.version)).toEqual(['1.0.0']);
      expect(store.selection()).toEqual({ starterId: 'inventory', version: '1.0.0' });
    });

    it('lists versions up to the one on offer', async () => {
      service.listInstalled.mockReturnValue(of([{ ...installed[0], offeredVersion: '1.1.0', upgradeAvailable: true }]));
      await store.loadInstalled();
      store.select('inventory');
      expect(store.selectableVersions().map((v) => v.version)).toEqual(['1.1.0', '1.0.0']);
    });

    it('cannot preview the version that is installed already', () => {
      store.select('inventory', '1.0.0');
      expect(store.selectionInstalled()).toBe(true);
      expect(store.canPreview()).toBe(false);
    });

    it('lists every version of a starter that is not installed', () => {
      store.select('sail');
      expect(store.selectableVersions().map((v) => v.version)).toEqual(['2.0.0']);
      expect(store.canPreview()).toBe(true);
    });
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

  describe('upgrading an installed starter', () => {
    const wouldUpgrade: UpgradeReport = {
      dryRun: true,
      status: 'would-apply',
      starterId: 'inventory',
      fromVersion: '1.0.0',
      toVersion: '1.1.0',
      items: [{ kind: 'entity', key: 'item', action: 'replace' }],
    };
    const upgraded: UpgradeReport = { ...wouldUpgrade, dryRun: false, status: 'applied' };

    it('cannot apply before a preview has come back', () => {
      expect(store.upgradeReport()).toBeUndefined();
      expect(store.canApplyUpgrade()).toBe(false);
    });

    it('previews the upgrade to the version on offer, and then applies exactly that version', async () => {
      service.upgrade.mockReturnValueOnce(of(wouldUpgrade)).mockReturnValueOnce(of(upgraded));

      await store.previewUpgrade('inventory');

      expect(service.upgrade).toHaveBeenCalledWith('inventory', true, undefined);
      expect(store.upgradeReport()).toEqual(wouldUpgrade);
      expect(store.canApplyUpgrade()).toBe(true);

      service.listInstalled.mockReturnValue(of([{ ...installed[0], version: '1.1.0' }]));
      await store.applyUpgrade();

      expect(service.upgrade).toHaveBeenLastCalledWith('inventory', false, '1.1.0');
      expect(store.upgradeReport()).toEqual(upgraded);
      expect(store.installed()[0].version).toBe('1.1.0');
      expect(store.canApplyUpgrade()).toBe(false);
    });

    it('keeps Apply disabled after a refused preview', async () => {
      const refused: UpgradeReport = { dryRun: true, status: 'rejected', errors: [{ message: 'No newer version' }] };
      service.upgrade.mockReturnValue(of(refused));

      await store.previewUpgrade('inventory');

      expect(store.upgradeReport()).toEqual(refused);
      expect(store.canApplyUpgrade()).toBe(false);
    });

    it('previewing another starter discards the report about the previous one', async () => {
      service.upgrade.mockReturnValue(of(wouldUpgrade));
      await store.previewUpgrade('inventory');

      service.upgrade.mockReturnValue(new Promise(() => undefined) as never);
      void store.previewUpgrade('sail');

      expect(store.upgradeStarterId()).toBe('sail');
      expect(store.upgradeReport()).toBeUndefined();
    });

    it('reports a failure that carries no report as an error', async () => {
      service.upgrade.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable' })));

      await store.previewUpgrade('inventory');

      expect(store.error()).toBeTruthy();
      expect(store.isBusy()).toBe(false);
      expect(store.canApplyUpgrade()).toBe(false);
    });

    it('does nothing when applying without a starter chosen', async () => {
      await store.applyUpgrade();

      expect(service.upgrade).not.toHaveBeenCalled();
    });
  });

  describe('removing a domain starter', () => {
    const removal: ImportReport = { dryRun: true, status: 'would-apply', items: [{ kind: 'entity', key: 'inv-item', action: 'delete' }] };

    it('previews first, and can apply only what the preview showed', async () => {
      expect(store.canApplyUninstall()).toBe(false);
      service.uninstall.mockReturnValueOnce(of(removal));

      await store.previewUninstall('inventory');

      expect(service.uninstall).toHaveBeenCalledWith('inventory', true);
      expect(store.uninstallStarterId()).toBe('inventory');
      expect(store.uninstallReport()).toEqual(removal);
      expect(store.canApplyUninstall()).toBe(true);
    });

    it('removes for real, then shows the applied report and reloads what is installed', async () => {
      service.uninstall.mockReturnValueOnce(of(removal));
      await store.previewUninstall('inventory');
      service.uninstall.mockReturnValueOnce(of({ ...removal, dryRun: false, status: 'applied' }));
      service.listInstalled.mockClear();

      await store.applyUninstall();

      expect(service.uninstall).toHaveBeenLastCalledWith('inventory', false);
      expect(store.uninstallReport()?.status).toBe('applied');
      expect(service.listInstalled).toHaveBeenCalledOnce();
      expect(store.canApplyUninstall()).toBe(false);
    });

    it('cannot remove what the dry run refused, and shows why', async () => {
      const refused: ImportReport = { dryRun: true, status: 'rejected', errors: [{ message: 'still has data' }] };
      service.uninstall.mockReturnValueOnce(of(refused));

      await store.previewUninstall('inventory');

      expect(store.uninstallReport()).toEqual(refused);
      expect(store.canApplyUninstall()).toBe(false);
    });

    it('drops the previous removal when another is previewed or the removal is cancelled', async () => {
      service.uninstall.mockReturnValueOnce(of(removal));
      await store.previewUninstall('inventory');
      service.uninstall.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));

      await store.previewUninstall('sail');

      expect(store.uninstallStarterId()).toBe('sail');
      expect(store.uninstallReport()).toBeUndefined();
      expect(store.error()).toBeDefined();

      service.uninstall.mockReturnValueOnce(of(removal));
      await store.previewUninstall('inventory');
      store.cancelUninstall();
      expect(store.uninstallStarterId()).toBeUndefined();
      expect(store.uninstallReport()).toBeUndefined();
    });
  });
});
