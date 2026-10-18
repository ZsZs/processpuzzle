import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { readTables } from '../../domain/calculation-source';
import { ORDER_TOTAL_DTO, SERVICE_ROOT } from '../../domain/test-calculation-definition';
import { byTestId, configureCalculationTabTest, flushDefinitions } from '../calculation-tab.testing';
import { CalculationTablesTabComponent } from './calculation-tables-tab.component';

describe('CalculationTablesTabComponent', () => {
  let fixture: ComponentFixture<CalculationTablesTabComponent>;
  let controller: HttpTestingController;

  const root = () => fixture.nativeElement as HTMLElement;
  const working = () => (fixture.componentInstance as unknown as { working(): string }).working();
  const tableOf = () => readTables(working())[0];

  async function open(rows: object[] = [ORDER_TOTAL_DTO]): Promise<void> {
    configureCalculationTabTest();
    fixture = TestBed.createComponent(CalculationTablesTabComponent);
    fixture.componentRef.setInput('entityId', 'order-total');
    fixture.detectChanges();
    controller = flushDefinitions(rows);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function change(testId: string, value: string): void {
    const element = byTestId(root(), testId) as HTMLInputElement | HTMLSelectElement;
    element.value = value;
    element.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function click(testId: string): void {
    byTestId(root(), testId)?.click();
    fixture.detectChanges();
  }

  beforeEach(async () => open());

  it('shows the table as a grid of rules', () => {
    expect(byTestId(root(), 'calculation-table-volume-discount')).not.toBeNull();
    expect((byTestId(root(), 'calculation-table-hit-policy') as HTMLSelectElement).value).toBe('FIRST');
    expect((byTestId(root(), 'calculation-table-cell-0-1') as HTMLInputElement).value).toBe('"GOLD"');
    expect((byTestId(root(), 'calculation-table-result-0') as HTMLInputElement).value).toBe('0.1');
  });

  it('rewrites the source on every edit, keeping its comments', () => {
    change('calculation-table-hit-policy', 'UNIQUE');
    change('calculation-table-output', 'rate');
    change('calculation-table-cell-1-0', '');
    change('calculation-table-result-1', '0.01');
    change('calculation-table-input-1', 'tier');

    expect(tableOf()).toMatchObject({ hitPolicy: 'UNIQUE', output: 'rate', inputs: ['subtotal', 'tier'] });
    expect(tableOf().rules[1]).toEqual({ when: ['-', '-'], result: '0.01' });
    expect(working()).toContain('# Discount tiers');
  });

  it('adds, moves and removes rules and inputs', () => {
    click('calculation-table-add-rule');
    expect(tableOf().rules).toHaveLength(3);
    (byTestId(root(), 'calculation-table-rule-2')?.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(tableOf().rules[1].result).toBe('0');
    click('calculation-table-remove-rule-0');
    expect(tableOf().rules).toHaveLength(2);

    click('calculation-table-add-input');
    expect(tableOf().inputs).toEqual(['subtotal', 'customerTier', 'input']);
    expect(tableOf().rules.every((rule) => rule.when.length === 3)).toBe(true);
    (byTestId(root(), 'calculation-table-grid')?.querySelectorAll('thead th button')[0] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(tableOf().inputs).toEqual(['customerTier', 'input']);
  });

  it('adds, renames and removes tables', () => {
    click('calculation-table-add');
    expect(readTables(working()).map((table) => table.name)).toEqual(['volume-discount', 'new-table']);
    click('calculation-table-add');
    change('calculation-table-name', 'tiers');
    expect(readTables(working()).map((table) => table.name)).toEqual(['volume-discount', 'new-table', 'tiers']);
    click('calculation-table-remove');
    click('calculation-table-remove');
    click('calculation-table-remove');
    expect(byTestId(root(), 'calculation-tables-none')).not.toBeNull();
  });

  it('saves the rewritten source', async () => {
    change('calculation-table-hit-policy', 'COLLECT');
    click('calculation-tables-save');
    const put = controller.expectOne(`${SERVICE_ROOT}/calculation-definitions/order-total`);
    expect(put.request.body.source).toContain('hitPolicy: COLLECT');
    put.flush({ ...ORDER_TOTAL_DTO, source: put.request.body.source });
    await fixture.whenStable();
  });

  it('refuses to edit a source that is not YAML', async () => {
    TestBed.resetTestingModule();
    await open([{ ...ORDER_TOTAL_DTO, source: 'calculation: [' }]);

    expect(byTestId(root(), 'calculation-tables-unreadable')).not.toBeNull();
  });
});
