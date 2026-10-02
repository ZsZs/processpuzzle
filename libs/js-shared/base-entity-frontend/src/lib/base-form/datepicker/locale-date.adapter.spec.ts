import { TestBed } from '@angular/core/testing';
import { DateAdapter } from '@angular/material/core';
import { TranslocoService } from '@jsverse/transloco';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { LocaleDateAdapter, provideLocaleDateAdapter } from './locale-date.adapter';

describe('LocaleDateAdapter', () => {
  let adapter: DateAdapter<Date>;
  const born = new Date(1960, 11, 9);

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideTranslocoTesting({ translations: {} }), provideLocaleDateAdapter()] });
    adapter = TestBed.inject(DateAdapter);
  });

  it('is the adapter provideLocaleDateAdapter registers', () => {
    expect(adapter).toBeInstanceOf(LocaleDateAdapter);
  });

  it('parses ISO input under any locale, a bare day as local midnight', () => {
    adapter.setLocale('hu');
    expect(adapter.parse('1960-12-09', null)).toEqual(born);
    expect(adapter.parse('2026-01-15T09:30:00Z', null)?.toISOString()).toBe('2026-01-15T09:30:00.000Z');
  });

  it('parses numeric input in the order of the locale', () => {
    adapter.setLocale('de');
    expect(adapter.parse('09.12.1960', null)).toEqual(born);
    adapter.setLocale('hu');
    expect(adapter.parse('1960. 12. 09.', null)).toEqual(born);
    adapter.setLocale('en-US');
    expect(adapter.parse('12/9/1960', null)).toEqual(born);
  });

  it('parses a month written as a name', () => {
    adapter.setLocale('de');
    expect(adapter.parse('9. Dez. 1960', null)).toEqual(born);
    adapter.setLocale('en-US');
    expect(adapter.parse('Friday, December 9, 1960', null)).toEqual(born);
  });

  it('expands a two-digit year', () => {
    adapter.setLocale('de');
    expect(adapter.parse('09.12.60', null)).toEqual(born);
    expect(adapter.parse('15.01.26', null)).toEqual(new Date(2026, 0, 15));
    expect(adapter.parse('15.01.49', null)).toEqual(new Date(2049, 0, 15));
    expect(adapter.parse('15.01.50', null)).toEqual(new Date(1950, 0, 15));
  });

  it('refuses a day that does not exist rather than rolling it over', () => {
    adapter.setLocale('de');
    const date = adapter.parse('31.02.2026', null);
    if (date === null) throw new Error('Expected an invalid Date rather than null');
    expect(adapter.isValid(date)).toBe(false);
  });

  it('answers empty input with null', () => {
    expect(adapter.parse('  ', null)).toBeNull();
  });

  it('deserializes a bare ISO day as local midnight', () => {
    expect(adapter.deserialize('1960-12-09')).toEqual(born);
  });

  it('follows the active Transloco language', () => {
    TestBed.inject(TranslocoService).setActiveLang('hu');
    expect(adapter.format(born, { dateStyle: 'short' })).toBe('1960. 12. 09.');
  });
});
