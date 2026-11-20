import { Occurrence } from './calendar.types';

/** How many distinct calendar colors the theme defines (`--pp-calendar-color-0` … `-5`). */
export const CALENDAR_COLOR_COUNT = 6;

/** What FullCalendar is given for one occurrence. A subset of its `EventInput`, so this file needs no FullCalendar import. */
export interface CalendarEventInput {
  id: string;
  title: string;
  /** ISO instant for a timed event, `YYYY-MM-DD` for an all-day one. */
  start: string;
  end: string;
  allDay: boolean;
  /** Whether the caller may drag and resize it; FullCalendar then offers the handles. */
  editable: boolean;
  color: string;
  /** Space separated. FullCalendar 7 calls it `className` and takes a string; v6's `classNames` and its arrays are silently ignored. */
  className: string;
  extendedProps: {
    entryId: string;
    calendarKey: string;
    busy: boolean;
    mine: boolean;
    canEdit: boolean;
    location?: string;
    category?: string;
  };
}

/** The CSS color of the calendar at {@code index} among those shown; the theme maps the variables to its palette. */
export function calendarColor(index: number): string {
  return `var(--pp-calendar-color-${Math.max(0, index) % CALENDAR_COLOR_COUNT})`;
}

/** Unique across calendars and, for a recurring entry, across its occurrences. */
function eventId(occurrence: Occurrence): string {
  const parts = [occurrence.calendarKey, occurrence.entryId];
  if (occurrence.recurrenceId) parts.push(occurrence.recurrenceId);
  return parts.join(':');
}

/**
 * One occurrence as a calendar event. A busy occurrence carries no title from the server, so it shows
 * {@code busyLabel}; it is never editable, whatever else says so.
 *
 * @param colorIndex position of the occurrence's calendar among the calendars shown
 */
export function toCalendarEvent(occurrence: Occurrence, colorIndex: number, busyLabel: string): CalendarEventInput {
  const allDay = occurrence.allDay;
  const canEdit = !occurrence.busy && occurrence.canEdit === true;
  const className = ['pp-calendar__event'];
  if (occurrence.busy) className.push('pp-calendar__event--busy');
  if (occurrence.mine) className.push('pp-calendar__event--mine');
  if (occurrence.invited) className.push('pp-calendar__event--invited');
  return {
    id: eventId(occurrence),
    title: occurrence.busy ? busyLabel : (occurrence.title ?? ''),
    start: (allDay ? occurrence.startDate : occurrence.start) ?? '',
    end: (allDay ? occurrence.endDate : occurrence.end) ?? '',
    allDay,
    editable: canEdit,
    color: calendarColor(colorIndex),
    className: className.join(' '),
    extendedProps: {
      entryId: occurrence.entryId,
      calendarKey: occurrence.calendarKey,
      busy: occurrence.busy,
      mine: occurrence.mine === true,
      canEdit,
      ...(occurrence.location ? { location: occurrence.location } : {}),
      ...(occurrence.category ? { category: occurrence.category } : {}),
    },
  };
}
