import { ANIMATION_MODULE_TYPE } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { TranslocoService } from '@jsverse/transloco';
import { BaseEntity, BaseEntityAttrDescriptor, BaseEntityDescriptorRegistry, BaseFormNavigatorSingletonStore, FormControlType } from '@processpuzzle/base-entity';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { NGXLogger } from 'ngx-logging-kit';
import { firstValueFrom, Observable, of, Subject, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { CalculationOutput, EvaluationResult } from '../../domain/calculation-api';
import { CalculationApiService } from '../../domain/calculation-api.service';
import { CalculationOutputRegistry } from '../../domain/calculation-output.registry';
import { CALCULATED_DEBOUNCE_MS, CalculatedControlComponent } from './calculated-control.component';

interface Order extends BaseEntity {
  id?: string;
  orderNumber?: string;
  total?: number;
}

/**
 * The CALCULATED control of an `Order`'s form, with the API and the output registry stubbed — each has a spec of
 * its own — so what is asserted is what it asks for, when, and what it shows.
 */
describe('CalculatedControlComponent', () => {
  const output: CalculationOutput = { calculationId: 'order-total', attribute: 'total', decision: 'total', parameter: 'order' };
  const translations = { en: { 'base_calculation.calculated_control.badge': 'Calculated by {{calculation}}', 'base_calculation.calculated_control.error': 'Could not recalculate.' } };

  let fixture: ComponentFixture<CalculatedControlComponent<Order>>;
  let form: FormGroup;
  let evaluate: ReturnType<typeof vi.fn>;

  const value = () => (fixture.nativeElement.querySelector('[data-testid="calculated-control-value"]') as HTMLInputElement).value;
  const error = () => fixture.nativeElement.querySelector('[data-testid="calculated-control-error"]') as HTMLElement | null;

  async function render(options: { output?: CalculationOutput; entity?: Order; evaluation?: () => Observable<EvaluationResult> } = {}) {
    const entity: Order = options.entity ?? { id: 'o-1', orderNumber: 'ORD-1', total: 10 };
    const found = 'output' in options ? options.output : output;
    evaluate = vi.fn(options.evaluation ?? (() => of({ values: { total: 42 } })));

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [CalculatedControlComponent],
      providers: [
        provideTranslocoTesting({ translations }),
        { provide: ANIMATION_MODULE_TYPE, useValue: 'NoopAnimations' },
        { provide: NGXLogger, useValue: { warn: vi.fn(), error: vi.fn() } },
        { provide: BaseFormNavigatorSingletonStore, useValue: {} },
        { provide: BaseEntityDescriptorRegistry, useValue: {} },
        { provide: CalculationOutputRegistry, useValue: { outputFor: vi.fn(async () => found) } },
        { provide: CalculationApiService, useValue: { evaluate } },
      ],
    }).compileComponents();
    await firstValueFrom(TestBed.inject(TranslocoService).load('en'));

    form = new FormGroup({ orderNumber: new FormControl(entity.orderNumber), total: new FormControl({ value: entity.total, disabled: true }) });
    fixture = TestBed.createComponent(CalculatedControlComponent<Order>);
    fixture.componentRef.setInput('config', new BaseEntityAttrDescriptor('total', FormControlType.CALCULATED, 'Total'));
    fixture.componentRef.setInput('entity', entity);
    fixture.componentRef.setInput('entityName', 'Order');
    fixture.componentRef.setInput('value', entity.total);
    fixture.componentInstance.formGroup = form;
    for (let attempt = 0; attempt < 3; attempt++) {
      fixture.detectChanges();
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve));
    }
  }

  /** Types into the form, and lets the debounce and the call it starts run to the end. */
  async function edit(orderNumber: string, wait = CALCULATED_DEBOUNCE_MS): Promise<void> {
    vi.useFakeTimers();
    form.get('orderNumber')?.setValue(orderNumber);
    await vi.advanceTimersByTimeAsync(wait);
    vi.useRealTimers();
    fixture.detectChanges();
  }

  afterEach(() => vi.useRealTimers());

  it('shows the stored value read-only, with the calculation named in the badge', async () => {
    await render();

    expect(value()).toBe('10');
    expect((fixture.nativeElement.querySelector('[data-testid="calculated-control-value"]') as HTMLInputElement).readOnly).toBe(true);
    expect(fixture.nativeElement.querySelector('[data-testid="calculated-control-badge"]')).not.toBeNull();
    expect(evaluate).not.toHaveBeenCalled();
  });

  it('recomputes the released calculation from the unsaved form once it has been quiet, and shows the result', async () => {
    await render();

    await edit('ORD-2');

    expect(evaluate).toHaveBeenCalledTimes(1);
    expect(evaluate).toHaveBeenCalledWith('order-total', { order: 'o-1' }, 'released', undefined, {
      order: { id: 'o-1', orderNumber: 'ORD-2', total: 10 },
    });
    expect(value()).toBe('42');
    expect(form.get('total')?.value).toBe(42);
  });

  it('waits for the debounce before asking', async () => {
    await render();

    await edit('ORD-2', CALCULATED_DEBOUNCE_MS - 1);

    expect(evaluate).not.toHaveBeenCalled();
  });

  it('writes the result without an event, so it neither loops nor dirties the form', async () => {
    await render();
    const changes = vi.fn();
    form.valueChanges.subscribe(changes);

    await edit('ORD-2');

    expect(changes).toHaveBeenCalledTimes(1);
    expect(evaluate).toHaveBeenCalledTimes(1);
  });

  it('sends no parameter for an object that has no id yet, only the unsaved payload', async () => {
    await render({ entity: { orderNumber: 'ORD-1' } });

    await edit('ORD-2');

    expect(evaluate).toHaveBeenCalledWith('order-total', {}, 'released', undefined, { order: { orderNumber: 'ORD-2', total: undefined } });
  });

  it('drops the answer of a call a newer edit has replaced', async () => {
    const first = new Subject<EvaluationResult>();
    let calls = 0;
    await render({ evaluation: () => (++calls === 1 ? first : of({ values: { total: 7 } })) });

    await edit('ORD-2');
    await edit('ORD-3');
    first.next({ values: { total: 99 } });
    fixture.detectChanges();

    expect(value()).toBe('7');
  });

  it('keeps the last value and says so when the evaluation fails', async () => {
    await render({ evaluation: () => throwError(() => new Error('boom')) });

    await edit('ORD-2');

    expect(value()).toBe('10');
    expect(error()?.textContent).toContain('Could not recalculate.');
  });

  it('treats a stopped evaluation, or one without the decision, as a failure', async () => {
    await render({ evaluation: () => of({ values: {}, error: 'decision x failed' }) });

    await edit('ORD-2');

    expect(value()).toBe('10');
    expect(error()).not.toBeNull();
  });

  it('clears the error once a later evaluation succeeds', async () => {
    let calls = 0;
    await render({ evaluation: () => (++calls === 1 ? throwError(() => new Error('boom')) : of({ values: { total: 5 } })) });

    await edit('ORD-2');
    await edit('ORD-3');

    expect(error()).toBeNull();
    expect(value()).toBe('5');
  });

  it('is a static display without an output that names an entity parameter', async () => {
    await render({ output: { ...output, parameter: null } });

    await edit('ORD-2');

    expect(evaluate).not.toHaveBeenCalled();
    expect(value()).toBe('10');
  });

  it('is a static display for an attribute no calculation writes', async () => {
    await render({ output: undefined });

    await edit('ORD-2');

    expect(evaluate).not.toHaveBeenCalled();
  });
});
