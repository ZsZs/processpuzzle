import { TestBed } from '@angular/core/testing';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { CalculationTraceComponent, format } from './calculation-trace.component';

describe('CalculationTraceComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideTranslocoTesting({ translations: { en: {} } })] }));

  it('lists the values and every decision, the failing one with its error', async () => {
    const fixture = TestBed.createComponent(CalculationTraceComponent);
    fixture.componentRef.setInput('result', {
      values: { total: 10, label: 'x' },
      trace: [
        { decision: 'total', kind: 'EXPR', scope: 'scalar', value: 10 },
        { decision: 'broken', kind: 'TABLE', scope: 'scalar', error: 'rules [1, 2] match' },
      ],
      error: 'decision broken failed',
    });
    fixture.detectChanges();
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('[data-testid="calculation-values"]')?.textContent).toContain('total');
    expect(root.querySelectorAll('[data-testid="calculation-trace"] tbody tr')).toHaveLength(2);
    expect(root.querySelector('tr.error')?.textContent).toContain('rules [1, 2] match');
    expect(root.querySelector('[data-testid="calculation-result-error"]')).not.toBeNull();
  });

  it('formats values for reading', () => {
    expect(format(undefined)).toBe('—');
    expect(format(null)).toBe('—');
    expect(format(3)).toBe('3');
    expect(format([1, 2])).toBe('[\n 1,\n 2\n]');
  });
});
