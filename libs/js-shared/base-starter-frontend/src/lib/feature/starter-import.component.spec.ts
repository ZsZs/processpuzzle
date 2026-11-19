import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogStarter, ImportReport, InstalledStarter, UpgradeReport } from '../domain/starter';
import { StarterService } from '../domain/starter.service';
import { StarterImportComponent } from './starter-import.component';

const translations = {
  en: {
    'base_starter.import.preview': 'Preview',
    'base_starter.import.apply': 'Install',
    'base_starter.import.catalogEmpty': 'No starters yet.',
    'base_starter.import.deprecated': 'deprecated',
    'base_starter.import.status.would-apply': 'Would install',
    'base_starter.import.status.applied': 'Installed',
    'base_starter.import.status.rejected': 'Rejected',
    'base_starter.import.summary': '{{created}} to create, {{updated}} to update, {{deleted}} to delete',
    'base_starter.import.kind.entity': 'Entity',
    'base_starter.import.kind.rule': 'Rule',
    'base_starter.import.action.create': 'create',
    'base_starter.import.action.update': 'update',
    'base_starter.import.action.delete': 'delete',
    'base_starter.installed.empty': 'No starter yet.',
    'base_starter.installed.upgradeTo': 'Upgrade to {{version}}',
    'base_starter.installed.upToDate': 'up to date',
    'base_starter.installed.remove': 'Remove',
    'base_starter.installed.notRemovable': 'It replaced the definitions, so it cannot be removed.',
    'base_starter.import.kind.mount': 'Mount',
    'base_starter.import.kind.module': 'Module',
    'base_starter.uninstall.apply': 'Remove starter',
    'base_starter.uninstall.cancel': 'Cancel',
    'base_starter.uninstall.status.would-apply': 'Would remove',
    'base_starter.uninstall.status.applied': 'Removed',
    'base_starter.uninstall.status.rejected': 'Cannot remove',
    'base_starter.uninstall.summary': '{{removed}} to remove',
    'base_starter.upgrade.apply': 'Apply upgrade',
    'base_starter.upgrade.status.would-apply': 'Would upgrade',
    'base_starter.upgrade.status.applied': 'Upgraded',
    'base_starter.upgrade.status.rejected': 'Rejected',
    'base_starter.upgrade.summary': '{{changed}} to change, {{kept}} kept',
    'base_starter.upgrade.drafts': 'Review the drafts, then release.',
    'base_starter.upgrade.skipped': 'Left as they are: {{kinds}}',
    'base_starter.upgrade.migrations': '{{count}} migration steps will run.',
    'base_starter.upgrade.action.replace': 'replace',
    'base_starter.upgrade.action.keep-customized': 'kept (customized)',
  },
};

const catalog: CatalogStarter[] = [
  {
    id: 'inventory',
    name: 'Inventory',
    description: 'Items and where they are.',
    author: 'ProcessPuzzle',
    license: 'Apache-2.0',
    versions: [
      { version: '1.1.0', status: 'published' },
      { version: '1.0.0', status: 'deprecated' },
    ],
  },
];
const wouldApply: ImportReport = {
  dryRun: true,
  status: 'would-apply',
  starterId: 'inventory',
  version: '1.1.0',
  items: [
    { kind: 'entity', key: 'inventory-item', action: 'create' },
    { kind: 'rule', key: 'sku-format', action: 'update' },
    { kind: 'entity', key: 'old-thing', action: 'delete' },
  ],
};
const rejected: ImportReport = { dryRun: true, status: 'rejected', errors: [{ message: 'it holds 3 entity objects' }] };
const installed: InstalledStarter[] = [{ starterId: 'inventory', version: '1.1.0', installedAt: '2026-10-05T10:00:00Z', installedBy: 'alice', customizedDefinitions: 0 }];

describe('StarterImportComponent', () => {
  let fixture: ComponentFixture<StarterImportComponent>;
  let service: {
    upgrade: ReturnType<typeof vi.fn>;
    uninstall: ReturnType<typeof vi.fn>;
    install: ReturnType<typeof vi.fn>;
    listInstalled: ReturnType<typeof vi.fn>;
    listCatalog: ReturnType<typeof vi.fn>;
  };

  async function create(starters: CatalogStarter[], installedStarters: InstalledStarter[] = []): Promise<void> {
    service = { upgrade: vi.fn(), uninstall: vi.fn(), install: vi.fn(), listInstalled: vi.fn(() => of(installedStarters)), listCatalog: vi.fn(() => of(starters)) };
    await TestBed.configureTestingModule({
      imports: [StarterImportComponent],
      providers: [provideTranslocoTesting({ translations }), { provide: StarterService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(StarterImportComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  const host = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);

  async function change(id: string, value?: string): Promise<void> {
    const element = byTestId(id) as HTMLInputElement | HTMLSelectElement;
    if (value !== undefined) element.value = value;
    element.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function click(id: string): Promise<void> {
    byTestId(id)?.click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  describe('with a catalog', () => {
    beforeEach(() => create(catalog));

    it('lists the catalog and keeps the actions disabled until a starter is picked', () => {
      expect(byTestId('catalog-starter-inventory')?.textContent).toContain('Apache-2.0');
      expect(byTestId('installed-starters-empty')?.textContent).toContain('No starter yet.');
      expect(byTestId('starter-version')).toBeNull();
      expect((byTestId('starter-preview') as HTMLButtonElement).disabled).toBe(true);
      expect((byTestId('starter-apply') as HTMLButtonElement).disabled).toBe(true);
    });

    it('previews the picked starter, deletions included, and then installs it', async () => {
      await change('catalog-select-inventory');
      expect(byTestId('starter-version')?.textContent).toContain('1.0.0 (deprecated)');
      service.install.mockReturnValueOnce(of(wouldApply));

      await click('starter-preview');

      expect(service.install).toHaveBeenCalledWith({ starterId: 'inventory', version: '1.1.0' }, true);
      expect(byTestId('import-report-status')?.textContent).toContain('Would install');
      expect(byTestId('import-report-summary')?.textContent).toContain('1 to create, 1 to update, 1 to delete');
      expect(byTestId('import-report-items')?.textContent).toContain('old-thing');
      expect((byTestId('starter-apply') as HTMLButtonElement).disabled).toBe(false);

      service.install.mockReturnValueOnce(of({ ...wouldApply, dryRun: false, status: 'applied' }));
      service.listInstalled.mockReturnValue(of(installed));
      await click('starter-apply');

      expect(service.install).toHaveBeenLastCalledWith({ starterId: 'inventory', version: '1.1.0' }, false);
      expect(byTestId('import-report-status')?.textContent).toContain('Installed');
      expect(byTestId('installed-starter-inventory')?.textContent).toContain('alice');
      expect((byTestId('starter-apply') as HTMLButtonElement).disabled).toBe(true);
    });

    it('installs the version picked in the version list', async () => {
      await change('catalog-select-inventory');
      await change('starter-version', '1.0.0');
      service.install.mockReturnValueOnce(of({ ...wouldApply, version: '1.0.0' }));

      await click('starter-preview');

      expect(service.install).toHaveBeenCalledWith({ starterId: 'inventory', version: '1.0.0' }, true);
    });

    it('lists the reasons of a rejected preview and keeps Install disabled', async () => {
      await change('catalog-select-inventory');
      service.install.mockReturnValueOnce(of(rejected));

      await click('starter-preview');

      expect(byTestId('import-report-status')?.textContent).toContain('Rejected');
      expect(byTestId('import-report-errors')?.textContent).toContain('it holds 3 entity objects');
      expect(byTestId('import-report-summary')).toBeNull();
      expect((byTestId('starter-apply') as HTMLButtonElement).disabled).toBe(true);
    });
  });

  it('says so when the catalog is empty', async () => {
    await create([]);

    expect(byTestId('starter-catalog-empty')?.textContent).toContain('No starters yet.');
    expect(byTestId('starter-catalog')).toBeNull();
  });

  describe('an upgrade is on offer', () => {
    const upgradeOffered: InstalledStarter[] = [
      { starterId: 'inventory', version: '1.0.0', installedAt: '2026-10-05T10:00:00Z', customizedDefinitions: 1, offeredVersion: '1.1.0', upgradeAvailable: true },
      { starterId: 'sail', version: '2.0.0', installedAt: '2026-10-05T10:00:00Z', upgradeAvailable: false },
    ];
    const upgradePreview: UpgradeReport = {
      dryRun: true,
      status: 'would-apply',
      starterId: 'inventory',
      fromVersion: '1.0.0',
      toVersion: '1.1.0',
      skippedKinds: ['calculation'],
      migrationSteps: [{ kind: 'entity', key: 'item', operation: 'renameAttribute', subject: 'name' }],
      items: [
        { kind: 'entity', key: 'item', action: 'replace' },
        { kind: 'rule', key: 'sku-format', action: 'keep-customized', note: 'changed by the organization; its version stays' },
      ],
    };

    beforeEach(() => create(catalog, upgradeOffered));

    it('offers the upgrade only for the starter that has one', () => {
      expect(byTestId('upgrade-inventory')?.textContent).toContain('Upgrade to 1.1.0');
      expect(byTestId('upgrade-sail')).toBeNull();
      expect(byTestId('installed-starter-sail')?.textContent).toContain('up to date');
      expect(byTestId('upgrade-report')).toBeNull();
    });

    it('previews the upgrade, shows what is kept and why, and applies it', async () => {
      service.upgrade.mockReturnValueOnce(of(upgradePreview));

      await click('upgrade-inventory');

      expect(service.upgrade).toHaveBeenCalledWith('inventory', true, undefined);
      expect(byTestId('upgrade-report-status')?.textContent).toContain('Would upgrade');
      expect(byTestId('upgrade-report-summary')?.textContent).toContain('1 to change, 1 kept');
      expect(byTestId('upgrade-report-items')?.textContent).toContain('changed by the organization');
      expect(byTestId('upgrade-report-drafts')?.textContent).toContain('Review the drafts');
      expect(byTestId('upgrade-report-skipped')?.textContent).toContain('calculation');
      expect(byTestId('upgrade-report-migrations')?.textContent).toContain('1 migration steps will run.');
      expect(byTestId('upgrade-report-migrations')?.textContent).toContain('renameAttribute');
      expect(byTestId('upgrade-report-migrations')?.textContent).toContain('name');
      expect((byTestId('upgrade-apply') as HTMLButtonElement).disabled).toBe(false);

      service.upgrade.mockReturnValueOnce(of({ ...upgradePreview, dryRun: false, status: 'applied' }));
      service.listInstalled.mockReturnValue(of([{ ...upgradeOffered[0], version: '1.1.0', upgradeAvailable: false }, upgradeOffered[1]]));
      await click('upgrade-apply');

      expect(service.upgrade).toHaveBeenLastCalledWith('inventory', false, '1.1.0');
      expect(byTestId('upgrade-report-status')?.textContent).toContain('Upgraded');
      expect(byTestId('upgrade-inventory')).toBeNull();
      expect((byTestId('upgrade-apply') as HTMLButtonElement).disabled).toBe(true);
    });

    it('shows why a refused upgrade was refused, and cannot apply it', async () => {
      service.upgrade.mockReturnValueOnce(of({ dryRun: true, status: 'rejected', errors: [{ message: 'Upgrade to 1.0.5 first' }] } satisfies UpgradeReport));

      await click('upgrade-inventory');

      expect(byTestId('upgrade-report-errors')?.textContent).toContain('Upgrade to 1.0.5 first');
      expect(byTestId('upgrade-report-drafts')).toBeNull();
      expect((byTestId('upgrade-apply') as HTMLButtonElement).disabled).toBe(true);
    });
  });

  describe('warnings of an install', () => {
    beforeEach(() => create(catalog));

    it('shows what the install could not do for want of a place, and the mounts it made', async () => {
      await change('catalog-select-inventory');
      service.install.mockReturnValueOnce(
        of({
          ...wouldApply,
          items: [...(wouldApply.items ?? []), { kind: 'mount', key: 'inv-stock', action: 'create' }],
          warnings: ["The module 'inv-orders' is installed but not mounted: the path 'orders' is taken."],
        }),
      );

      await click('starter-preview');

      expect(byTestId('import-report-warnings')?.textContent).toContain("'inv-orders' is installed but not mounted");
      expect(byTestId('import-report-items')?.textContent).toContain('Mount');
      expect(byTestId('import-report-items')?.textContent).toContain('inv-stock');
    });

    it('shows no warnings list when there are none', async () => {
      await change('catalog-select-inventory');
      service.install.mockReturnValueOnce(of(wouldApply));

      await click('starter-preview');

      expect(byTestId('import-report-warnings')).toBeNull();
    });
  });

  describe('removing a domain starter', () => {
    const domain: InstalledStarter = { starterId: 'inventory', version: '1.0.0', installedAt: '2026-10-05T10:00:00Z', installedBy: 'alice', domainStarter: true };
    const replaced: InstalledStarter = { starterId: 'sail', version: '0.3.0', installedAt: '2026-10-05T10:00:00Z', installedBy: 'alice', domainStarter: false };
    const removal: ImportReport = {
      dryRun: true,
      status: 'would-apply',
      starterId: 'inventory',
      version: '1.0.0',
      items: [
        { kind: 'entity', key: 'inv-item', action: 'delete' },
        { kind: 'mount', key: 'inv-stock', action: 'delete' },
      ],
      warnings: ['The mounts are removed from the draft of the application; release it to remove them from the live one.'],
    };

    beforeEach(() => create([], [domain, replaced]));

    it('offers Remove for a domain starter only', () => {
      expect(byTestId('uninstall-inventory')).not.toBeNull();
      expect(byTestId('uninstall-sail')).toBeNull();
      expect(byTestId('not-removable-sail')).not.toBeNull();
    });

    it('previews what would go, in the words of a removal, and keeps Remove disabled until there is a preview', async () => {
      expect(byTestId('uninstall-report')).toBeNull();
      service.uninstall.mockReturnValueOnce(of(removal));

      await click('uninstall-inventory');

      expect(service.uninstall).toHaveBeenCalledWith('inventory', true);
      const report = byTestId('uninstall-report')!;
      expect(report.textContent).toContain('Would remove');
      expect(report.textContent).not.toContain('Would install');
      expect(report.textContent).toContain('2 to remove');
      expect(report.textContent).toContain('inv-stock');
      expect(report.textContent).toContain('release it to remove them from the live one');
      expect((byTestId('uninstall-apply') as HTMLButtonElement).disabled).toBe(false);
    });

    it('removes the starter once confirmed, and the list loses it', async () => {
      service.uninstall.mockReturnValueOnce(of(removal));
      await click('uninstall-inventory');
      service.uninstall.mockReturnValueOnce(of({ ...removal, dryRun: false, status: 'applied' }));
      service.listInstalled.mockReturnValue(of([replaced]));

      await click('uninstall-apply');

      expect(service.uninstall).toHaveBeenLastCalledWith('inventory', false);
      expect(byTestId('uninstall-report')?.textContent).toContain('Removed');
      expect(byTestId('installed-starter-inventory')).toBeNull();
      expect((byTestId('uninstall-apply') as HTMLButtonElement).disabled).toBe(true);
    });

    it('shows why a removal is refused and cannot be confirmed', async () => {
      service.uninstall.mockReturnValueOnce(of({ dryRun: true, status: 'rejected', errors: [{ message: 'The starter\'s definitions still have data: 4 entity objects.' }] }));

      await click('uninstall-inventory');

      expect(byTestId('uninstall-report')?.textContent).toContain('Cannot remove');
      expect(byTestId('uninstall-report')?.textContent).toContain('still have data');
      expect((byTestId('uninstall-apply') as HTMLButtonElement).disabled).toBe(true);
    });

    it('drops the removal when cancelled', async () => {
      service.uninstall.mockReturnValueOnce(of(removal));
      await click('uninstall-inventory');

      await click('uninstall-cancel');

      expect(byTestId('uninstall-report')).toBeNull();
      expect(service.uninstall).toHaveBeenCalledTimes(1);
    });
  });
});
