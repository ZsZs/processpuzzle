import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ImportReport, InstalledStarter } from '../domain/starter';
import { StarterService } from '../domain/starter.service';
import { StarterImportComponent } from './starter-import.component';

const translations = {
  en: {
    'base_starter.import.preview': 'Preview',
    'base_starter.import.apply': 'Import',
    'base_starter.import.status.would-apply': 'Would import',
    'base_starter.import.status.applied': 'Imported',
    'base_starter.import.status.rejected': 'Rejected',
    'base_starter.import.summary': '{{created}} to create, {{updated}} to update',
    'base_starter.import.kind.entity': 'Entity',
    'base_starter.import.kind.rule': 'Rule',
    'base_starter.import.action.create': 'create',
    'base_starter.import.action.update': 'update',
    'base_starter.installed.empty': 'No starter yet.',
  },
};

const wouldApply: ImportReport = {
  dryRun: true,
  status: 'would-apply',
  starterId: 'inventory',
  version: '1.0.0',
  items: [
    { kind: 'entity', key: 'inventory-item', action: 'create' },
    { kind: 'rule', key: 'sku-format', action: 'update' },
  ],
};
const rejected: ImportReport = { dryRun: true, status: 'rejected', errors: [{ file: 'rules/r.yaml', message: "extends unknown rule 'x'" }] };
const installed: InstalledStarter[] = [{ starterId: 'inventory', version: '1.0.0', installedAt: '2026-10-05T10:00:00Z', installedBy: 'alice', customizedDefinitions: 2 }];

describe('StarterImportComponent', () => {
  let fixture: ComponentFixture<StarterImportComponent>;
  let service: { importBundle: ReturnType<typeof vi.fn>; listInstalled: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    service = { importBundle: vi.fn(), listInstalled: vi.fn(() => of([])) };
    await TestBed.configureTestingModule({
      imports: [StarterImportComponent],
      providers: [provideTranslocoTesting({ translations }), { provide: StarterService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(StarterImportComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  const host = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const byTestId = (id: string) => host().querySelector<HTMLElement>(`[data-testid="${id}"]`);

  async function pick(file: File): Promise<void> {
    const input = byTestId('starter-bundle-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function click(id: string): Promise<void> {
    byTestId(id)?.click();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('shows the empty installed list and disabled actions before a bundle is picked', () => {
    expect(byTestId('installed-starters-empty')?.textContent).toContain('No starter yet.');
    expect((byTestId('starter-preview') as HTMLButtonElement).disabled).toBe(true);
    expect((byTestId('starter-apply') as HTMLButtonElement).disabled).toBe(true);
  });

  it('previews the picked bundle and then imports it', async () => {
    const bundle = new File(['zip'], 'inventory.zip');
    await pick(bundle);
    service.importBundle.mockReturnValueOnce(of(wouldApply));

    await click('starter-preview');

    expect(service.importBundle).toHaveBeenCalledWith(bundle, true);
    expect(byTestId('import-report-status')?.textContent).toContain('Would import');
    expect(byTestId('import-report-summary')?.textContent).toContain('1 to create, 1 to update');
    expect(byTestId('import-report-items')?.textContent).toContain('inventory-item');
    expect((byTestId('starter-apply') as HTMLButtonElement).disabled).toBe(false);

    service.importBundle.mockReturnValueOnce(of({ ...wouldApply, dryRun: false, status: 'applied' }));
    service.listInstalled.mockReturnValue(of(installed));
    await click('starter-apply');

    expect(service.importBundle).toHaveBeenLastCalledWith(bundle, false);
    expect(byTestId('import-report-status')?.textContent).toContain('Imported');
    expect(byTestId('installed-starter-inventory')?.textContent).toContain('alice');
    expect((byTestId('starter-apply') as HTMLButtonElement).disabled).toBe(true);
  });

  it('lists the reasons of a rejected preview and keeps Import disabled', async () => {
    await pick(new File(['zip'], 'bad.zip'));
    service.importBundle.mockReturnValueOnce(of(rejected));

    await click('starter-preview');

    expect(byTestId('import-report-status')?.textContent).toContain('Rejected');
    expect(byTestId('import-report-errors')?.textContent).toContain("rules/r.yaml");
    expect(byTestId('import-report-errors')?.textContent).toContain("extends unknown rule 'x'");
    expect(byTestId('import-report-summary')).toBeNull();
    expect((byTestId('starter-apply') as HTMLButtonElement).disabled).toBe(true);
  });
});
