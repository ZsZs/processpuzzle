import { describe, expect, it } from 'vitest';
import { Occurrence } from './calendar.types';
import { calendarColor, toCalendarEvent } from './occurrence-events';

const TIMED: Occurrence = {
  entryId: 'e-1',
  calendarKey: 'team',
  allDay: false,
  busy: false,
  title: 'Planning',
  location: 'Room 1',
  category: 'meeting',
  start: '2026-12-01T09:00:00Z',
  end: '2026-12-01T10:00:00Z',
  mine: true,
  canEdit: true,
};

describe('toCalendarEvent', () => {
  it('maps a timed occurrence to instants and keeps what a click needs in the extended props', () => {
    expect(toCalendarEvent(TIMED, 0, 'Busy')).toEqual({
      id: 'team:e-1',
      title: 'Planning',
      start: '2026-12-01T09:00:00Z',
      end: '2026-12-01T10:00:00Z',
      allDay: false,
      editable: true,
      color: 'var(--pp-calendar-color-0)',
      className: 'pp-calendar__event pp-calendar__event--mine',
      extendedProps: { entryId: 'e-1', calendarKey: 'team', busy: false, mine: true, canEdit: true, location: 'Room 1', category: 'meeting' },
    });
  });

  it('maps an all-day occurrence to its dates, the end exclusive', () => {
    const holiday: Occurrence = { entryId: 'e-2', calendarKey: 'team', allDay: true, busy: false, title: 'Holiday', startDate: '2026-12-24', endDate: '2026-12-27' };

    expect(toCalendarEvent(holiday, 1, 'Busy')).toMatchObject({ start: '2026-12-24', end: '2026-12-27', allDay: true, editable: false, color: 'var(--pp-calendar-color-1)' });
  });

  it('shows a busy occurrence under the busy label, never editable, whatever the server flagged', () => {
    const busy: Occurrence = { ...TIMED, busy: true, title: undefined, location: undefined, category: undefined, canEdit: true };

    const event = toCalendarEvent(busy, 0, 'Busy');

    expect(event.title).toBe('Busy');
    expect(event.editable).toBe(false);
    expect(event.extendedProps.canEdit).toBe(false);
    expect(event.className.split(' ')).toContain('pp-calendar__event--busy');
    expect(event.extendedProps).not.toHaveProperty('location');
  });

  it('keeps two occurrences of one entry on different calendars apart by id', () => {
    expect(toCalendarEvent({ ...TIMED, calendarKey: 'a' }, 0, 'Busy').id).not.toBe(toCalendarEvent({ ...TIMED, calendarKey: 'b' }, 0, 'Busy').id);
  });

  it('adds the recurrence id to the id of an occurrence of a recurring entry', () => {
    expect(toCalendarEvent({ ...TIMED, recurrenceId: '2026-12-08' }, 0, 'Busy').id).toBe('team:e-1:2026-12-08');
  });

  it('is never draggable, though the caller may edit the series from its dialog', () => {
    const occurrence = { ...TIMED, recurrenceId: '2026-12-08' };

    const event = toCalendarEvent(occurrence, 0, 'Busy');

    expect(event.editable).toBe(false);
    expect(event.extendedProps.canEdit).toBe(true);
    expect(event.className.split(' ')).toContain('pp-calendar__event--recurring');
  });

  it('is not editable without the server saying so', () => {
    expect(toCalendarEvent({ ...TIMED, canEdit: undefined }, 0, 'Busy').editable).toBe(false);
  });
});

describe('invited occurrences', () => {
  it('carry the invited class, and only those do', () => {
    expect(toCalendarEvent({ ...TIMED, invited: true }, 0, 'Busy').className).toContain('pp-calendar__event--invited');
    expect(toCalendarEvent(TIMED, 0, 'Busy').className).not.toContain('pp-calendar__event--invited');
  });
});

describe('calendarColor', () => {
  it('cycles through the six theme colors', () => {
    expect(calendarColor(0)).toBe('var(--pp-calendar-color-0)');
    expect(calendarColor(5)).toBe('var(--pp-calendar-color-5)');
    expect(calendarColor(6)).toBe('var(--pp-calendar-color-0)');
    expect(calendarColor(-1)).toBe('var(--pp-calendar-color-0)');
  });
});
