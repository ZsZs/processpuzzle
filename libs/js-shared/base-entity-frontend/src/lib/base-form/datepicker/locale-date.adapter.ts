import { DestroyRef, EnvironmentProviders, inject, Injectable, makeEnvironmentProviders, provideEnvironmentInitializer } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DateAdapter, MAT_DATE_FORMATS, MAT_NATIVE_DATE_FORMATS, NativeDateAdapter } from '@angular/material/core';
import { TranslocoService } from '@jsverse/transloco';
import { toDateValue } from '../../base-entity/date-format';

type DatePart = 'year' | 'month' | 'day';

/**
 * A {@link NativeDateAdapter} that reads typed dates the way the active locale writes them.
 *
 * The native adapter parses with `Date.parse`, which only understands ISO and US-ordered input: `09.12.1960`
 * typed under `de` comes back invalid, and `1960. 12. 09.` under `hu` too. This one accepts, in order:
 * ISO (`1960-12-09`, `1960-12-09T10:00:00Z`), then numbers in the locale's year/month/day order with any
 * separators, a month written as a name (`9. Dez. 1960`, `1960. dec. 9.`) included. Anything else falls back
 * to the native parse. Formatting is the native adapter's already: it calls `Intl` with the display options.
 */
@Injectable()
export class LocaleDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, parseFormat?: unknown): Date | null {
    if (typeof value !== 'string') return super.parse(value, parseFormat);
    const text = value.trim();
    if (text === '') return null;
    return toDateValue(text) ?? this.parseLocaleOrdered(text) ?? super.parse(text, parseFormat);
  }

  /** `YYYY-MM-DD` as local midnight; the native adapter reads it as UTC midnight, the day before west of Greenwich. */
  override deserialize(value: unknown): Date | null {
    return (typeof value === 'string' && toDateValue(value)) || super.deserialize(value);
  }

  private parseLocaleOrdered(text: string): Date | null {
    const tokens = text.split(/[^\p{L}\p{N}]+/u).filter((token) => token !== '');
    const numbers = tokens.filter((token) => /^\d+$/.test(token)).map(Number);
    const month = tokens.filter((token) => !/^\d+$/.test(token)).map((word) => this.monthOf(word)).find((index) => index !== undefined);

    const order = this.partOrder();
    const parts: Partial<Record<DatePart, number>> = {};
    if (month !== undefined && numbers.length === 2) {
      const [first, second] = order.filter((part) => part !== 'month');
      parts[first] = numbers[0];
      parts[second] = numbers[1];
      parts.month = month;
    } else if (month === undefined && numbers.length === 3) {
      order.forEach((part, index) => (parts[part] = part === 'month' ? numbers[index] - 1 : numbers[index]));
    } else return null;

    const year = parts.year! < 100 ? parts.year! + (parts.year! < 50 ? 2000 : 1900) : parts.year!;
    const date = new Date(year, parts.month!, parts.day!);
    // Out-of-range parts (`31.02.`) roll over in the Date constructor; refuse them instead of moving the day.
    if (date.getFullYear() !== year || date.getMonth() !== parts.month || date.getDate() !== parts.day) return this.invalid();
    return date;
  }

  private partOrder(): DatePart[] {
    return new Intl.DateTimeFormat(this.locale, { year: 'numeric', month: '2-digit', day: '2-digit' })
      .formatToParts(new Date(2000, 10, 22))
      .map((part) => part.type)
      .filter((type): type is DatePart => type === 'year' || type === 'month' || type === 'day');
  }

  /** The month index a word names in the locale, by its long or short name; weekday names match nothing. */
  private monthOf(word: string): number | undefined {
    const normalized = word.toLocaleLowerCase(this.locale);
    for (const style of ['long', 'short'] as const) {
      const index = this.getMonthNames(style).findIndex((name) => name.toLocaleLowerCase(this.locale).replace(/\.$/, '') === normalized);
      if (index >= 0) return index;
    }
    if (normalized.length < 3) return undefined;
    const index = this.getMonthNames('long').findIndex((name) => name.toLocaleLowerCase(this.locale).startsWith(normalized));
    return index >= 0 ? index : undefined;
  }
}

/**
 * Registers {@link LocaleDateAdapter} in place of `provideNativeDateAdapter()`, and keeps its locale on the
 * active Transloco language, so an open datepicker re-renders on a language switch. Without Transloco the
 * adapter keeps `MAT_DATE_LOCALE`.
 */
export function provideLocaleDateAdapter(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: DateAdapter, useClass: LocaleDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: MAT_NATIVE_DATE_FORMATS },
    provideEnvironmentInitializer(() => {
      const adapter = inject(DateAdapter);
      const transloco = inject(TranslocoService, { optional: true });
      transloco?.langChanges$.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((lang) => adapter.setLocale(lang));
    }),
  ]);
}
