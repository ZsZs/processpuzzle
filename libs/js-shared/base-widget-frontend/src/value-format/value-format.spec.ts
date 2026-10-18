import { describe, expect, it } from 'vitest';
import { formatValue, valueAtPath } from './value-format';

describe('valueAtPath', () => {
  const order = { number: 'A-1', customer: { address: { city: 'Budapest' } }, lineItems: [{ qty: 2 }] };

  it('walks nested objects', () => {
    expect(valueAtPath(order, 'customer.address.city')).toBe('Budapest');
  });

  it('is the root itself for an empty path', () => {
    expect(valueAtPath(order, '')).toBe(order);
    expect(valueAtPath(order, undefined)).toBe(order);
  });

  it('returns a terminal array, but does not walk into one', () => {
    expect(valueAtPath(order, 'lineItems')).toEqual([{ qty: 2 }]);
    expect(valueAtPath(order, 'lineItems.qty')).toBeUndefined();
  });

  it('is undefined when anything along the way is missing', () => {
    expect(valueAtPath(order, 'customer.phone.number')).toBeUndefined();
    expect(valueAtPath(null, 'a')).toBeUndefined();
  });
});

describe('formatValue', () => {
  it('renders nothing for an absent value', () => {
    expect(formatValue(undefined, 'number', 'en')).toBe('');
    expect(formatValue(null, 'text', 'en')).toBe('');
  });

  it('formats numbers and currencies for the locale', () => {
    expect(formatValue(1234.5, 'number', 'en')).toBe('1,234.5');
    expect(formatValue('1234.5', 'currency:EUR', 'en')).toBe('€1,234.50');
  });

  it('falls back to the plain value when the number cannot be read or the currency is unknown', () => {
    expect(formatValue('n/a', 'number', 'en')).toBe('n/a');
    expect(formatValue(true, 'number', 'en')).toBe('true');
    expect(formatValue(3, 'currency:NOPE', 'en')).toBe('3');
  });

  it('formats dates and date-times, keeping an unparsable date as written', () => {
    expect(formatValue('2026-10-18', 'date', 'en')).toBe('Oct 18, 2026');
    expect(formatValue(new Date(2026, 9, 18, 14, 30), 'datetime', 'en')).toMatch(/Oct 18, 2026.*2:30/);
    expect(formatValue('soon', 'date', 'en')).toBe('soon');
    expect(formatValue({}, 'date', 'en')).toBe('');
  });

  it('joins the scalar values of an object and the items of an array', () => {
    expect(formatValue({ street: 'Fő utca 1', city: 'Budapest', geo: { lat: 1 }, zip: '' }, 'text', 'en')).toBe('Fő utca 1, Budapest');
    expect(formatValue(['a', 'b'], undefined, 'en')).toBe('a, b');
  });
});
