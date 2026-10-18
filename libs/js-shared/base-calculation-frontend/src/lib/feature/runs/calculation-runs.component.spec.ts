import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CALCULATION_RUN, SERVICE_ROOT, TRACE } from '../../domain/test-calculation-definition';
import { byTestId, configureCalculationTabTest, settle as settleFixture } from '../calculation-tab.testing';
import { CalculationRunsComponent } from './calculation-runs.component';

describe('CalculationRunsComponent', () => {
  let fixture: ComponentFixture<CalculationRunsComponent>;
  let controller: HttpTestingController;

  const root = () => fixture.nativeElement as HTMLElement;

  const settle = () => settleFixture(fixture);

  beforeEach(async () => {
    configureCalculationTabTest();
    fixture = TestBed.createComponent(CalculationRunsComponent);
    controller = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    controller.expectOne(`${SERVICE_ROOT}/calculation-runs`).flush([CALCULATION_RUN, { ...CALCULATION_RUN, id: 'run-2', status: 'FAILED', error: 'boom' }]);
    await settle();
  });

  it('lists the runs newest first', () => {
    expect(byTestId(root(), 'calculation-runs-list')?.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(byTestId(root(), 'calculation-run-run-2')?.textContent).toContain('FAILED');
  });

  it('filters by calculation and subject', async () => {
    const calculation = byTestId(root(), 'calculation-runs-filter-calculation') as HTMLInputElement;
    calculation.value = 'order-total';
    calculation.dispatchEvent(new Event('change'));
    const subject = byTestId(root(), 'calculation-runs-filter-subject') as HTMLInputElement;
    subject.value = 'o-1';
    subject.dispatchEvent(new Event('change'));
    byTestId(root(), 'calculation-runs-refresh')?.click();

    const request = controller.expectOne((each) => each.url === `${SERVICE_ROOT}/calculation-runs`);
    expect(request.request.params.get('calculationId')).toBe('order-total');
    expect(request.request.params.get('subjectId')).toBe('o-1');
    request.flush([]);
    await settle();
    expect(byTestId(root(), 'calculation-runs-none')).not.toBeNull();
  });

  it('opens a run with its trace and source, and freezes it', async () => {
    byTestId(root(), 'calculation-run-run-1')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculation-runs/run-1/trace`).flush(TRACE);
    await settle();

    expect(byTestId(root(), 'calculation-run-details')?.textContent).toContain('order-total');
    expect(byTestId(root(), 'calculation-run-source')?.textContent).toContain('calculation: order-total');

    byTestId(root(), 'calculation-run-freeze')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculation-runs/run-1/freeze`).flush({ ...CALCULATION_RUN, frozen: true, frozenAt: '2026-10-18T11:00:00Z' });
    await settle();

    expect((byTestId(root(), 'calculation-run-freeze') as HTMLButtonElement).disabled).toBe(true);
    expect(byTestId(root(), 'calculation-run-run-1')?.textContent).toContain('✓');
  });

  it('does not offer to freeze a failed run, and shows why a request failed', async () => {
    byTestId(root(), 'calculation-run-run-2')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculation-runs/run-2/trace`).flush({ errorText: 'gone' }, { status: 404, statusText: 'Not Found' });
    await settle();

    expect((byTestId(root(), 'calculation-run-freeze') as HTMLButtonElement).disabled).toBe(true);
    expect(byTestId(root(), 'calculation-runs-failure')?.textContent).toContain('gone');
  });
});
