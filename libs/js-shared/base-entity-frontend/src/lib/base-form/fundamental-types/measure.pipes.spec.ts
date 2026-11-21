import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { describe, expect, it } from 'vitest';
import { DimensionsPipe, MeasurePipe, MoneyPipe, QuantityPipe } from './measure.pipes';

@Component({
  selector: 'host-component',
  standalone: true,
  imports: [QuantityPipe, MoneyPipe, DimensionsPipe, MeasurePipe],
  template: `<span data-id="q">{{ quantity() | ppQuantity }}</span
    ><span data-id="m">{{ money() | ppMoney }}</span
    ><span data-id="d">{{ dimensions() | ppDimensions }}</span
    ><span data-id="x">{{ any() | ppMeasure }}</span>`,
})
class HostComponent {
  quantity = signal<unknown>({ amount: '1234.5', unit: 'kg' });
  money = signal<unknown>({ amount: '1234.5', currency: 'EUR' });
  dimensions = signal<unknown>({ width: '1.2', height: '0.8', unit: 'm' });
  any = signal<unknown>({ amount: '89.88', currency: 'EUR' });
}

describe('measure pipes', () => {
  it('write the values in the active language’s notation, and re-render when it changes', async () => {
    await TestBed.configureTestingModule({ imports: [HostComponent], providers: [provideTranslocoTesting({ translations: {} })] }).compileComponents();
    const fixture = TestBed.createComponent(HostComponent);
    const text = (id: string) => (fixture.nativeElement as HTMLElement).querySelector(`[data-id="${id}"]`)?.textContent?.trim();

    fixture.detectChanges();
    expect([text('q'), text('m'), text('d')]).toEqual(['1,234.5 kg', '€1,234.50', '1.2 × 0.8 m']);

    TestBed.inject(TranslocoService).setActiveLang('de');
    fixture.detectChanges();
    expect(text('q')).toBe('1.234,5 kg');
    expect(text('m')).toContain('1.234,50');
    expect(text('d')).toBe('1,2 × 0,8 m');

    fixture.componentInstance.quantity.set('nothing');
    fixture.componentInstance.money.set(null);
    fixture.componentInstance.dimensions.set({});
    fixture.detectChanges();
    expect([text('q'), text('m'), text('d')]).toEqual(['', '', '']);

    // Whatever a calculated attribute holds: a measure in its own notation, anything else as it is.
    expect(text('x')).toContain('89,88');
    fixture.componentInstance.any.set(42);
    fixture.detectChanges();
    expect(text('x')).toBe('42');
    fixture.componentInstance.any.set(undefined);
    fixture.detectChanges();
    expect(text('x')).toBe('');
  });
});
