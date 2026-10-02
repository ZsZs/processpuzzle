import { describe, expect, it } from 'vitest';
import { effectiveDateFormat, formatDateValue, intlOptionsOf, isDateOnly, toDateValue, toStoredDate } from './date-format';

describe('date-format', () => {
  describe('effectiveDateFormat', () => {
    it('defaults a DATE to a medium date and a DATE_TIME to a medium date with a short time', () => {
      expect(effectiveDateFormat(undefined)).toEqual({ dateStyle: 'medium', timeStyle: 'none' });
      expect(effectiveDateFormat({}, 'DATE_TIME')).toEqual({ dateStyle: 'medium', timeStyle: 'short' });
    });

    it('fills only the styles the attribute leaves out', () => {
      expect(effectiveDateFormat({ dateFormat: { dateStyle: 'short' } }, 'DATE_TIME')).toEqual({ dateStyle: 'short', timeStyle: 'short' });
      expect(effectiveDateFormat({ dateFormat: { timeStyle: 'long' } })).toEqual({ dateStyle: 'medium', timeStyle: 'long' });
    });
  });

  describe('formatDateValue', () => {
    const born = new Date(1960, 11, 9);
    const intl = (locale: string, options: Intl.DateTimeFormatOptions, date: Date) => new Intl.DateTimeFormat(locale, options).format(date);

    it('renders short in en, de and hu', () => {
      expect(formatDateValue(born, { dateStyle: 'short' }, 'en-GB')).toBe('09/12/1960');
      expect(formatDateValue(born, { dateStyle: 'short' }, 'de')).toBe(intl('de', { dateStyle: 'short' }, born));
      expect(formatDateValue(born, { dateStyle: 'short' }, 'hu')).toBe('1960. 12. 09.');
    });

    it('reads a bare ISO day as that local day, whatever the time zone', () => {
      expect(formatDateValue('1960-12-09', { dateStyle: 'medium' }, 'de')).toBe('09.12.1960');
    });

    it('renders the time of a DATE_TIME format', () => {
      const value = new Date(2026, 0, 15, 9, 30);
      expect(formatDateValue(value.toISOString(), { dateStyle: 'short', timeStyle: 'short' }, 'de')).toBe(intl('de', { dateStyle: 'short', timeStyle: 'short' }, value));
    });

    it('returns a value that is not a date unchanged', () => {
      expect(formatDateValue('not-a-date', { dateStyle: 'short' }, 'en')).toBe('not-a-date');
      expect(formatDateValue(42, { dateStyle: 'short' }, 'en')).toBe(42);
      expect(formatDateValue(undefined, { dateStyle: 'short' }, 'en')).toBeUndefined();
    });
  });

  describe('intlOptionsOf', () => {
    it('leaves a none style out, and never ends up with no style at all', () => {
      expect(intlOptionsOf({ dateStyle: 'none', timeStyle: 'short' })).toEqual({ timeStyle: 'short' });
      expect(intlOptionsOf({ dateStyle: 'long', timeStyle: 'none' })).toEqual({ dateStyle: 'long' });
      expect(intlOptionsOf({ dateStyle: 'none', timeStyle: 'none' })).toEqual({ dateStyle: 'medium' });
    });
  });

  describe('storage', () => {
    it('saves a date-only format as the local day and any other as the instant', () => {
      const picked = new Date(1960, 11, 9, 23, 30);
      expect(isDateOnly({ dateStyle: 'short', timeStyle: 'none' })).toBe(true);
      expect(toStoredDate(picked, { dateStyle: 'short', timeStyle: 'none' })).toBe('1960-12-09');
      expect(toStoredDate(picked, { dateStyle: 'short', timeStyle: 'short' })).toBe(picked);
    });

    it('accepts dates and ISO strings only', () => {
      expect(toDateValue('2026-01-15T09:30:00Z')?.toISOString()).toBe('2026-01-15T09:30:00.000Z');
      expect(toDateValue(new Date(Number.NaN))).toBeUndefined();
      expect(toDateValue('12/9/1960')).toBeUndefined();
      expect(toDateValue('')).toBeUndefined();
    });
  });
});
