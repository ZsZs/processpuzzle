import { describe, expect, it } from 'vitest';
import { ExchangeRates, isoDateOf } from './exchange-rates';

describe('ExchangeRates helpers', () => {
  it('names the currencies some rate mentions, alphabetically', () => {
    const rates = new ExchangeRates([
      { from: 'USD', to: 'EUR', rate: '0.9', validFrom: '2026-01-01' },
      { from: 'EUR', to: 'HUF', rate: '400', validFrom: '2026-01-01' },
    ]);

    expect(rates.currencies()).toEqual(['EUR', 'HUF', 'USD']);
    expect(ExchangeRates.NONE.currencies()).toEqual([]);
  });

  it('writes a date as the ISO text a rate is looked up by, in the local calendar', () => {
    expect(isoDateOf(new Date(2026, 6, 1, 23, 59))).toBe('2026-07-01');
    expect(isoDateOf(new Date(2026, 0, 9))).toBe('2026-01-09');
    expect(isoDateOf()).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
