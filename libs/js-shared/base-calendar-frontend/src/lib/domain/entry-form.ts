import { AttendeeDraft, CalendarEntry, CalendarEntryInput, CalendarInfo, CalendarRecurrence, CalendarSlot, EntryKind, EntryVisibility, RecurrenceFrequency } from './calendar.types';
import { datePart, instantToLocal, localToInstant, nextDay, plusMinutes, previousDay, toInstant } from './zoned-time';

/** The most occurrences a series may have, as the server accepts. */
export const MAX_RECURRENCE_COUNT = 500;

/** The kinds the dialog offers: what the calendar allows, minus the recurring kind (a repetition is a separate choice), never none. */
export function bookableKinds(calendar: CalendarInfo): EntryKind[] {
  const kinds = (calendar.allowedKinds ?? ['TIMED', 'ALL_DAY']).filter((kind) => kind !== 'RECURRING');
  return kinds.length > 0 ? kinds : ['TIMED'];
}

/** Whether the calendar takes repeating entries; the dialog offers no repetition otherwise. */
export function repeatsAllowed(calendar: CalendarInfo): boolean {
  return calendar.allowedKinds?.includes('RECURRING') === true;
}

/** The kind of the template an entry is made from: a single entry is its own kind, a series is the kind it repeats. */
export function templateKindOf(entry: Pick<CalendarEntry, 'kind' | 'startDate'>): 'TIMED' | 'ALL_DAY' {
  if (entry.kind === 'RECURRING') return entry.startDate ? 'ALL_DAY' : 'TIMED';
  return entry.kind === 'ALL_DAY' ? 'ALL_DAY' : 'TIMED';
}

/** `NEVER` is a single entry; the others make the entry RECURRING. */
export type RepeatFrequency = 'NEVER' | RecurrenceFrequency;

/** When a repetition stops: never, after a number of occurrences, or after the last day given. */
export type RepeatEnds = 'NEVER' | 'AFTER_COUNT' | 'ON_DATE';

export interface RepeatForm {
  frequency: RepeatFrequency;
  ends: RepeatEnds;
  /** Set when `ends` is AFTER_COUNT; null while the field is blank. */
  count: number | null;
  /** `YYYY-MM-DD`, inclusive; set when `ends` is ON_DATE. */
  until: string;
}

/** The repetition of an entry that does not repeat. The count is what a user starts from when they choose one. */
export const NO_REPEAT: RepeatForm = { frequency: 'NEVER', ends: 'NEVER', count: 10, until: '' };

/**
 * What the entry dialog edits. A timed entry uses the two wall-clock fields (`YYYY-MM-DDTHH:mm`, in the organization's
 * zone), an all-day entry the two days. The last day is *inclusive* here, because that is how a person says it;
 * the API's end date is the day after. `kind` is the kind of the template, whether or not it repeats.
 */
export interface EntryFormValue {
  kind: EntryKind;
  title: string;
  description: string;
  location: string;
  category: string;
  visibility: EntryVisibility;
  startLocal: string;
  endLocal: string;
  startDay: string;
  lastDay: string;
  repeat: RepeatForm;
  attendees: AttendeeDraft[];
  /** Whether the entry this form was made from came with an attendee list; a form without invitees then clears it. */
  hadAttendees: boolean;
}

/** All-day for a whole-day click when the calendar takes all-day entries, else timed when it takes those, else what it takes. */
function kindForSlot(kinds: readonly EntryKind[], allDay: boolean): EntryKind {
  if (allDay && kinds.includes('ALL_DAY')) return 'ALL_DAY';
  return kinds.includes('TIMED') ? 'TIMED' : kinds[0];
}

/** How long a new timed entry lasts when the user only clicked: a slot of the calendar, else an hour. */
function defaultMinutes(calendar: CalendarInfo): number {
  return Math.max(calendar.slotMinutes ?? 60, calendar.minDurationMinutes ?? 0, 15);
}

/** The form of a new entry, starting from where the user clicked or dragged. */
export function formForSlot(calendar: CalendarInfo, slot: CalendarSlot): EntryFormValue {
  const zone = calendar.effectiveTimeZone;
  const kinds = bookableKinds(calendar);
  const kind = kindForSlot(kinds, slot.allDay);
  const base = { kind, title: '', description: '', location: '', category: '', visibility: 'PUBLIC' as EntryVisibility, repeat: { ...NO_REPEAT }, attendees: [], hadAttendees: false };

  if (slot.allDay) {
    const startDay = datePart(slot.start);
    const lastDay = slot.end > slot.start ? previousDay(datePart(slot.end)) : startDay;
    const midday = localToInstant(`${startDay}T09:00`, zone);
    return {
      ...base,
      startDay,
      lastDay: lastDay < startDay ? startDay : lastDay,
      startLocal: instantToLocal(midday, zone),
      endLocal: instantToLocal(plusMinutes(midday, defaultMinutes(calendar)), zone),
    };
  }
  const start = toInstant(slot.start, zone);
  const end = toInstant(slot.end, zone);
  const startLocal = instantToLocal(start, zone);
  return {
    ...base,
    startLocal,
    endLocal: instantToLocal(end > start ? end : plusMinutes(start, defaultMinutes(calendar)), zone),
    startDay: datePart(startLocal),
    lastDay: datePart(startLocal),
  };
}

/** The repetition an entry has, read back into the form; a single entry, or one without its recurrence, does not repeat. */
function repeatOf(entry: CalendarEntry): RepeatForm {
  const recurrence = entry.recurrence;
  if (!recurrence) return { ...NO_REPEAT };
  const base = { ...NO_REPEAT, frequency: recurrence.frequency };
  if (recurrence.count != null) return { ...base, ends: 'AFTER_COUNT', count: recurrence.count };
  if (recurrence.until) return { ...base, ends: 'ON_DATE', until: recurrence.until };
  return base;
}

/** The form of an existing entry the caller may edit. */
export function formForEntry(calendar: CalendarInfo, entry: CalendarEntry): EntryFormValue {
  const zone = calendar.effectiveTimeZone;
  const templateKind = templateKindOf(entry);
  const base = {
    kind: templateKind,
    title: entry.title ?? '',
    description: entry.description ?? '',
    location: entry.location ?? '',
    category: entry.category ?? '',
    visibility: entry.visibility ?? 'PUBLIC',
    repeat: repeatOf(entry),
    attendees: (entry.attendees ?? []).map((attendee) => ({ type: attendee.type, principal: attendee.principal ?? '', displayName: attendee.displayName, required: attendee.required })),
    hadAttendees: entry.attendees !== undefined,
  };
  if (templateKind === 'ALL_DAY' && entry.startDate && entry.endDate) {
    const startDay = datePart(entry.startDate);
    const midday = localToInstant(`${startDay}T09:00`, zone);
    return {
      ...base,
      startDay,
      lastDay: previousDay(datePart(entry.endDate)),
      startLocal: instantToLocal(midday, zone),
      endLocal: instantToLocal(plusMinutes(midday, 60), zone),
    };
  }
  const startLocal = entry.start ? instantToLocal(entry.start, zone) : '';
  return {
    ...base,
    startLocal,
    endLocal: entry.end ? instantToLocal(entry.end, zone) : '',
    startDay: datePart(startLocal),
    lastDay: datePart(startLocal),
  };
}

/** The recurrence the form asks for, or null when it does not repeat. A blank field is left out; the form is then incomplete. */
function recurrenceOf(repeat: RepeatForm): CalendarRecurrence | null {
  if (repeat.frequency === 'NEVER') return null;
  if (repeat.ends === 'AFTER_COUNT' && repeat.count !== null) return { frequency: repeat.frequency, count: repeat.count };
  if (repeat.ends === 'ON_DATE' && repeat.until) return { frequency: repeat.frequency, until: repeat.until };
  return { frequency: repeat.frequency };
}

/**
 * The form as the API's input. Blank optional text becomes null, the zone of a timed entry is the organization's, and
 * the inclusive last day becomes the exclusive end date. A repeating form is sent as RECURRING with its template and
 * recurrence. A form with an unusable time yields no input.
 *
 * @param version the version the entry was read at, when updating
 */
export function toEntryInput(value: EntryFormValue, calendar: CalendarInfo, version?: number | null): CalendarEntryInput | null {
  const zone = calendar.effectiveTimeZone;
  const recurrence = recurrenceOf(value.repeat);
  const common = {
    kind: recurrence ? 'RECURRING' : value.kind,
    title: value.title.trim(),
    description: blankToNull(value.description),
    location: blankToNull(value.location),
    category: blankToNull(value.category),
    visibility: value.visibility,
    version: version ?? null,
    ...(recurrence ? { recurrence } : {}),
  } satisfies Partial<CalendarEntryInput>;
  const withAttendees = value.attendees.length > 0 || value.hadAttendees ? { ...common, attendees: value.attendees.map(({ type, principal, required }) => ({ type, principal, required })) } : common;
  if (value.kind === 'ALL_DAY') {
    if (!value.startDay || !value.lastDay) return null;
    return { ...withAttendees, startDate: value.startDay, endDate: nextDay(value.lastDay) };
  }
  if (!value.startLocal || !value.endLocal) return null;
  return { ...withAttendees, start: localToInstant(value.startLocal, zone), end: localToInstant(value.endLocal, zone), timeZone: zone };
}

/** Whether the form can be sent: a title, times that run forwards, and a repetition that says when it ends. The server still has the last word. */
export function isFormComplete(value: EntryFormValue): boolean {
  if (value.title.trim().length === 0) return false;
  if (!repeatIsComplete(value)) return false;
  if (value.kind === 'ALL_DAY') return value.startDay !== '' && value.lastDay !== '' && value.lastDay >= value.startDay;
  return value.startLocal !== '' && value.endLocal !== '' && value.endLocal > value.startLocal;
}

function repeatIsComplete(value: EntryFormValue): boolean {
  const { repeat } = value;
  if (repeat.frequency === 'NEVER') return true;
  if (repeat.ends === 'AFTER_COUNT') return repeat.count !== null && Number.isInteger(repeat.count) && repeat.count >= 1 && repeat.count <= MAX_RECURRENCE_COUNT;
  if (repeat.ends === 'ON_DATE') return repeat.until !== '' && repeat.until >= (value.kind === 'ALL_DAY' ? value.startDay : datePart(value.startLocal));
  return true;
}

function blankToNull(text: string): string | null {
  const trimmed = text.trim();
  return trimmed.length === 0 ? null : trimmed;
}
