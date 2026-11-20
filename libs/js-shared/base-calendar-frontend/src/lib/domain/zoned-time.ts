import { Temporal } from 'temporal-polyfill';

/**
 * Conversions between the instants the API speaks and the wall-clock values a form edits, in the organization's
 * time zone. All of it is done on {@link Temporal} values and never on {@code Date}, whose local zone is the
 * browser's, not the organization's: a person in another zone must still enter 09:00 as the office's 09:00.
 */

const OFFSET_SUFFIX = /(Z|[+-]\d{2}:?\d{2})$/i;

/** `YYYY-MM-DDTHH:mm` of an instant as the wall clock of {@code zone} shows it, for a `datetime-local` input. */
export function instantToLocal(instant: string, zone: string): string {
  const local = Temporal.Instant.from(instant).toZonedDateTimeISO(zone).toPlainDateTime();
  return local.toString({ smallestUnit: 'minute' });
}

/**
 * The instant at which {@code zone}'s wall clock reads {@code local} (`YYYY-MM-DDTHH:mm`). A wall-clock time that does
 * not exist (the hour skipped when clocks go forward) moves to the next valid one, and an ambiguous one (the hour
 * repeated when they go back) takes the earlier — {@code Temporal}'s `compatible` rule, which is what a calendar does.
 */
export function localToInstant(local: string, zone: string): string {
  return Temporal.PlainDateTime.from(local).toZonedDateTime(zone, { disambiguation: 'compatible' }).toInstant().toString();
}

/**
 * An ISO timestamp as an instant. FullCalendar reports a timed value in the calendar's zone, with its offset; a value
 * without an offset is read as the wall clock of {@code zone}.
 */
export function toInstant(value: string, zone: string): string {
  return OFFSET_SUFFIX.test(value) ? Temporal.Instant.from(value).toString() : localToInstant(value.slice(0, 16), zone);
}

/** The day after {@code date} (`YYYY-MM-DD`), as the exclusive end of a one-day entry. */
export function nextDay(date: string): string {
  return Temporal.PlainDate.from(date).add({ days: 1 }).toString();
}

/** The day before {@code date}: an exclusive end date as the last day a person means. */
export function previousDay(date: string): string {
  return Temporal.PlainDate.from(date).subtract({ days: 1 }).toString();
}

/** `YYYY-MM-DD` of an ISO timestamp or date, for a `date` input. Takes the date part as written. */
export function datePart(value: string): string {
  return value.slice(0, 10);
}

/** The instant {@code minutes} after {@code instant}. */
export function plusMinutes(instant: string, minutes: number): string {
  return Temporal.Instant.from(instant).add({ minutes }).toString();
}
