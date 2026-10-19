import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ORDER_TOTAL_DTO, SERVICE_ROOT } from '../../domain/test-calculation-definition';
import { byTestId, configureCalculationTabTest, flushDefinitions, settle } from '../calculation-tab.testing';
import { CalculationSourceTabComponent } from './calculation-source-tab.component';

describe('CalculationSourceTabComponent', () => {
  let fixture: ComponentFixture<CalculationSourceTabComponent>;
  let controller: HttpTestingController;

  const root = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    configureCalculationTabTest();
    fixture = TestBed.createComponent(CalculationSourceTabComponent);
    fixture.componentRef.setInput('entityId', 'order-total');
    fixture.detectChanges();
    controller = flushDefinitions();
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function edit(source: string): void {
    (fixture.componentInstance as unknown as { edit(source: string): void }).edit(source);
    fixture.detectChanges();
  }

  it('edits the selected definition, and has nothing to save until it changes', () => {
    expect(root().querySelector('.cm-content')?.textContent).toContain('calculation: order-total');
    expect((byTestId(root(), 'calculation-save') as HTMLButtonElement).disabled).toBe(true);

    edit(ORDER_TOTAL_DTO.source + '# more\n');

    expect(byTestId(root(), 'calculation-unsaved')).not.toBeNull();
    expect((byTestId(root(), 'calculation-save') as HTMLButtonElement).disabled).toBe(false);
  });

  it('validates and lists the decisions in evaluation order with what they require', async () => {
    byTestId(root(), 'calculation-validate')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculation-definitions/validate`).flush({
      valid: true,
      errors: [],
      order: ['lineTotal', 'total'],
      decisions: [
        { name: 'total', kind: 'EXPR', scope: 'scalar' },
        { name: 'lineTotal', kind: 'EXPR', scope: 'row:lines' },
      ],
      dependencies: [{ from: 'lineTotal', to: 'total' }],
    });
    await settle(fixture);

    expect(byTestId(root(), 'calculation-valid')).not.toBeNull();
    const rows = Array.from(byTestId(root(), 'calculation-decisions')?.querySelectorAll('tbody tr') ?? []).map((row) =>
      Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim()).filter((text) => text).join(' '),
    );
    expect(rows).toEqual(['1 lineTotal EXPR row:lines', '2 total EXPR scalar lineTotal']);
  });

  it('does not save a source that does not validate', async () => {
    edit('calculation: order-total\n');
    byTestId(root(), 'calculation-save')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculation-definitions/validate`).flush({ valid: false, errors: ['needs a decision'], decisions: [] });
    await settle(fixture);

    expect(byTestId(root(), 'calculation-errors')?.textContent).toContain('needs a decision');
    controller.expectNone(`${SERVICE_ROOT}/calculation-definitions/order-total`);
  });

  it('saves a valid source through the store, and shows a failure', async () => {
    edit(ORDER_TOTAL_DTO.source + '# more\n');
    byTestId(root(), 'calculation-save')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculation-definitions/validate`).flush({ valid: true, errors: [] });
    await settle(fixture);
    const put = controller.expectOne(`${SERVICE_ROOT}/calculation-definitions/order-total`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ source: ORDER_TOTAL_DTO.source + '# more\n', version: 4 });
    put.flush({ ...ORDER_TOTAL_DTO, source: ORDER_TOTAL_DTO.source + '# more\n', version: 5 });
    await settle(fixture);
    expect(byTestId(root(), 'calculation-unsaved')).toBeNull();

    byTestId(root(), 'calculation-validate')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculation-definitions/validate`).flush({ errorText: 'down' }, { status: 503, statusText: 'Unavailable' });
    await settle(fixture);
    expect(byTestId(root(), 'calculation-source-failure')?.textContent).toContain('down');
  });
});
