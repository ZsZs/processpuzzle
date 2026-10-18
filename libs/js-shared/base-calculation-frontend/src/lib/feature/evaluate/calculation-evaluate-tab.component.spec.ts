import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { CALCULATION_RUN, SERVICE_ROOT, TRACE } from '../../domain/test-calculation-definition';
import { byTestId, configureCalculationTabTest, flushDefinitions, settle as settleFixture } from '../calculation-tab.testing';
import { CalculationEvaluateTabComponent } from './calculation-evaluate-tab.component';

describe('CalculationEvaluateTabComponent', () => {
  let fixture: ComponentFixture<CalculationEvaluateTabComponent>;
  let controller: HttpTestingController;

  const root = () => fixture.nativeElement as HTMLElement;

  beforeEach(async () => {
    configureCalculationTabTest();
    fixture = TestBed.createComponent(CalculationEvaluateTabComponent);
    fixture.componentRef.setInput('entityId', 'order-total');
    fixture.detectChanges();
    controller = flushDefinitions();
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function enter(name: string, value: string): void {
    const input = byTestId(root(), `calculation-parameter-${name}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  const settle = () => settleFixture(fixture);

  it('offers a field per parameter', () => {
    expect(byTestId(root(), 'calculation-parameter-order')).not.toBeNull();
    expect(byTestId(root(), 'calculation-parameter-customerTier')?.getAttribute('placeholder')).toBe('string');
  });

  it('evaluates the draft with the values entered, leaving empty ones to their defaults', async () => {
    enter('order', 'o-1');
    enter('customerTier', ' ');
    byTestId(root(), 'calculation-evaluate')?.click();

    const request = controller.expectOne(`${SERVICE_ROOT}/calculations/order-total/evaluate`);
    expect(request.request.body).toEqual({ parameters: { order: 'o-1' }, revision: 'draft', source: undefined });
    request.flush({ values: { total: 89.88 }, trace: TRACE, error: 'decision x failed' });
    await settle();

    expect(byTestId(root(), 'calculation-values')?.textContent).toContain('89.88');
    expect(byTestId(root(), 'calculation-trace')?.textContent).toContain('row:lines');
    expect(byTestId(root(), 'calculation-result-error')?.textContent).toContain('decision x failed');
  });

  it('runs the released definition and shows the stored run with its trace', async () => {
    enter('order', 'o-1');
    byTestId(root(), 'calculation-run')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculations/order-total/runs`).flush(CALCULATION_RUN);
    await settle();
    controller.expectOne(`${SERVICE_ROOT}/calculation-runs/run-1/trace`).flush(TRACE);
    await settle();

    expect(byTestId(root(), 'calculation-run-status')?.className).toContain('ok');
    expect(byTestId(root(), 'calculation-trace')?.textContent).toContain('lineTotal');
  });

  it('shows why a request was refused', async () => {
    byTestId(root(), 'calculation-evaluate')?.click();
    controller.expectOne(`${SERVICE_ROOT}/calculations/order-total/evaluate`).flush({ errorText: 'parameter order needs a value' }, { status: 400, statusText: 'Bad Request' });
    await settle();

    expect(byTestId(root(), 'calculation-evaluate-failure')?.textContent).toContain('parameter order needs a value');
  });
});
