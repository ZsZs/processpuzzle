import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogStarter, ImportReport, InstalledStarter } from '../domain/starter';
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
  let service: { install: ReturnType<typeof vi.fn>; listInstalled: ReturnType<typeof vi.fn>; listCatalog: ReturnType<typeof vi.fn> };

  async function create(starters: CatalogStarter[]): Promise<void> {
    service = { install: vi.fn(), listInstalled: vi.fn(() => of([])), listCatalog: vi.fn(() => of(starters)) };
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
});
