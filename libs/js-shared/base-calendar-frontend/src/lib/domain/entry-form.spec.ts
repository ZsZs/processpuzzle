import { describe, expect, it } from 'vitest';
import { CalendarEntry, CalendarInfo } from './calendar.types';
import { bookableKinds, EntryFormValue, formForEntry, formForSlot, isFormComplete, toEntryInput } from './entry-form';

const CALENDAR: CalendarInfo = {
  key: 'team',
  name: 'Team',
  kind: 'TEAM',
  effectiveTimeZone: 'Europe/Budapest',
  allowedKinds: ['TIMED', 'ALL_DAY'],
  permissions: { canBook: true, canManage: false },
};

function value(overrides: Partial<EntryFormValue> = {}): EntryFormValue {
  return {
    kind: 'TIMED',
    title: 'Planning',
    description: '',
    location: '',
    category: '',
    visibility: 'PUBLIC',
    startLocal: '2026-12-01T10:00',
    endLocal: '2026-12-01T11:00',
    startDay: '2026-12-01',
    lastDay: '2026-12-01',
    ...overrides,
  };
}

describe('bookableKinds', () => {
  it('offers what the calendar allows, without the reserved recurring kind', () => {
    expect(bookableKinds({ ...CALENDAR, allowedKinds: ['TIMED', 'RECURRING'] })).toEqual(['TIMED']);
    expect(bookableKinds({ ...CALENDAR, allowedKinds: ['ALL_DAY', 'TIMED'] })).toEqual(['ALL_DAY', 'TIMED']);
  });

  it('never offers none', () => {
    expect(bookableKinds({ ...CALENDAR, allowedKinds: ['RECURRING'] })).toEqual(['TIMED']);
    expect(bookableKinds({ ...CALENDAR, allowedKinds: undefined })).toEqual(['TIMED', 'ALL_DAY']);
  });
});

describe('formForSlot', () => {
  it('starts a timed entry from the dragged range, in the organization wall clock', () => {
    const form = formForSlot(CALENDAR, { start: '2026-12-01T10:00:00+01:00', end: '2026-12-01T11:30:00+01:00', allDay: false });

    expect(form).toMatchObject({ kind: 'TIMED', startLocal: '2026-12-01T10:00', endLocal: '2026-12-01T11:30', title: '', visibility: 'PUBLIC' });
  });

  it('gives a click on a timed slot the length of a slot, an hour at least when the calendar has none', () => {
    const click = { start: '2026-12-01T10:00:00+01:00', end: '2026-12-01T10:00:00+01:00', allDay: false };

    expect(formForSlot(CALENDAR, click).endLocal).toBe('2026-12-01T11:00');
    expect(formForSlot({ ...CALENDAR, slotMinutes: 30 }, click).endLocal).toBe('2026-12-01T10:30');
    expect(formForSlot({ ...CALENDAR, slotMinutes: 15, minDurationMinutes: 45 }, click).endLocal).toBe('2026-12-01T10:45');
  });

  it('turns an exclusive all-day end into the inclusive last day', () => {
    const form = formForSlot(CALENDAR, { start: '2026-12-24', end: '2026-12-27', allDay: true });

    expect(form).toMatchObject({ kind: 'ALL_DAY', startDay: '2026-12-24', lastDay: '2026-12-26' });
  });

  it('makes a single clicked day a one-day entry', () => {
    const form = formForSlot(CALENDAR, { start: '2026-12-24', end: '2026-12-25', allDay: true });

    expect(form).toMatchObject({ startDay: '2026-12-24', lastDay: '2026-12-24' });
  });

  it('starts a timed entry on a calendar that takes no all-day ones, even from an all-day click', () => {
    const form = formForSlot({ ...CALENDAR, allowedKinds: ['TIMED'] }, { start: '2026-12-24', end: '2026-12-25', allDay: true });

    expect(form.kind).toBe('TIMED');
    expect(form.startLocal).toBe('2026-12-24T09:00');
  });
});

describe('formForEntry', () => {
  const timed: CalendarEntry = {
    id: 'e-1',
    calendarKey: 'team',
    kind: 'TIMED',
    busy: false,
    title: 'Planning',
    description: 'Quarterly',
    location: 'Room 1',
    category: 'meeting',
    visibility: 'PRIVATE',
    start: '2026-12-01T09:00:00Z',
    end: '2026-12-01T10:00:00Z',
    timeZone: 'Europe/Budapest',
    version: 3,
  };

  it('shows a timed entry in the organization wall clock', () => {
    expect(formForEntry(CALENDAR, timed)).toMatchObject({
      kind: 'TIMED',
      title: 'Planning',
      description: 'Quarterly',
      location: 'Room 1',
      category: 'meeting',
      visibility: 'PRIVATE',
      startLocal: '2026-12-01T10:00',
      endLocal: '2026-12-01T11:00',
    });
  });

  it('shows an all-day entry with an inclusive last day', () => {
    const holiday: CalendarEntry = { ...timed, kind: 'ALL_DAY', start: null, end: null, startDate: '2026-12-24', endDate: '2026-12-27' };

    expect(formForEntry(CALENDAR, holiday)).toMatchObject({ kind: 'ALL_DAY', startDay: '2026-12-24', lastDay: '2026-12-26' });
  });
});

describe('toEntryInput', () => {
  it('sends a timed entry as instants with the organization zone', () => {
    expect(toEntryInput(value({ description: ' Quarterly ' }), CALENDAR, 3)).toEqual({
      kind: 'TIMED',
      title: 'Planning',
      description: 'Quarterly',
      location: null,
      category: null,
      visibility: 'PUBLIC',
      version: 3,
      start: '2026-12-01T09:00:00Z',
      end: '2026-12-01T10:00:00Z',
      timeZone: 'Europe/Budapest',
    });
  });

  it('sends an all-day entry with the day after the last day as its end, and no instants', () => {
    const input = toEntryInput(value({ kind: 'ALL_DAY', startDay: '2026-12-24', lastDay: '2026-12-26' }), CALENDAR);

    expect(input).toMatchObject({ kind: 'ALL_DAY', startDate: '2026-12-24', endDate: '2026-12-27', version: null });
    expect(input).not.toHaveProperty('start');
    expect(input).not.toHaveProperty('timeZone');
  });

  it('round-trips an entry through the form', () => {
    const entry: CalendarEntry = {
      id: 'e-1',
      calendarKey: 'team',
      kind: 'TIMED',
      busy: false,
      title: 'Planning',
      start: '2026-07-01T09:00:00Z',
      end: '2026-07-01T10:30:00Z',
      version: 1,
    };

    const input = toEntryInput(formForEntry(CALENDAR, entry), CALENDAR, entry.version);

    expect(input).toMatchObject({ start: '2026-07-01T09:00:00Z', end: '2026-07-01T10:30:00Z', version: 1 });
  });

  it('yields nothing for a form without usable times', () => {
    expect(toEntryInput(value({ startLocal: '' }), CALENDAR)).toBeNull();
    expect(toEntryInput(value({ kind: 'ALL_DAY', lastDay: '' }), CALENDAR)).toBeNull();
  });
});

describe('isFormComplete', () => {
  it('needs a title and times that run forwards', () => {
    expect(isFormComplete(value())).toBe(true);
    expect(isFormComplete(value({ title: '   ' }))).toBe(false);
    expect(isFormComplete(value({ endLocal: '2026-12-01T10:00' }))).toBe(false);
    expect(isFormComplete(value({ endLocal: '2026-12-01T09:00' }))).toBe(false);
    expect(isFormComplete(value({ kind: 'ALL_DAY', startDay: '2026-12-02', lastDay: '2026-12-01' }))).toBe(false);
    expect(isFormComplete(value({ kind: 'ALL_DAY', startDay: '2026-12-01', lastDay: '2026-12-01' }))).toBe(true);
  });
});
