import { CalendarEntry, CalendarEntryInput, CalendarInfo, CalendarSlot, EntryKind, EntryVisibility } from './calendar.types';
import { datePart, instantToLocal, localToInstant, nextDay, plusMinutes, previousDay, toInstant } from './zoned-time';

/** The kinds the dialog offers: what the calendar allows, minus the reserved recurring kind, never none. */
export function bookableKinds(calendar: CalendarInfo): EntryKind[] {
  const kinds = (calendar.allowedKinds ?? ['TIMED', 'ALL_DAY']).filter((kind) => kind !== 'RECURRING');
  return kinds.length > 0 ? kinds : ['TIMED'];
}

/**
 * What the entry dialog edits. A timed entry uses the two wall-clock fields (`YYYY-MM-DDTHH:mm`, in the organization's
 * zone), an all-day entry the two days. The last day is *inclusive* here, because that is how a person says it;
 * the API's end date is the day after.
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
  const base = { kind, title: '', description: '', location: '', category: '', visibility: 'PUBLIC' as EntryVisibility };

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

/** The form of an existing entry the caller may edit. */
export function formForEntry(calendar: CalendarInfo, entry: CalendarEntry): EntryFormValue {
  const zone = calendar.effectiveTimeZone;
  const base = {
    kind: entry.kind,
    title: entry.title ?? '',
    description: entry.description ?? '',
    location: entry.location ?? '',
    category: entry.category ?? '',
    visibility: entry.visibility ?? 'PUBLIC',
  };
  if (entry.kind === 'ALL_DAY' && entry.startDate && entry.endDate) {
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

/**
 * The form as the API's input. Blank optional text becomes null, the zone of a timed entry is the organization's, and
 * the inclusive last day becomes the exclusive end date. A form with an unusable time yields no input.
 *
 * @param version the version the entry was read at, when updating
 */
export function toEntryInput(value: EntryFormValue, calendar: CalendarInfo, version?: number | null): CalendarEntryInput | null {
  const zone = calendar.effectiveTimeZone;
  const common = {
    kind: value.kind,
    title: value.title.trim(),
    description: blankToNull(value.description),
    location: blankToNull(value.location),
    category: blankToNull(value.category),
    visibility: value.visibility,
    version: version ?? null,
  };
  if (value.kind === 'ALL_DAY') {
    if (!value.startDay || !value.lastDay) return null;
    return { ...common, startDate: value.startDay, endDate: nextDay(value.lastDay) };
  }
  if (!value.startLocal || !value.endLocal) return null;
  return { ...common, start: localToInstant(value.startLocal, zone), end: localToInstant(value.endLocal, zone), timeZone: zone };
}

/** Whether the form can be sent: a title, and times that run forwards. The server still has the last word. */
export function isFormComplete(value: EntryFormValue): boolean {
  if (value.title.trim().length === 0) return false;
  if (value.kind === 'ALL_DAY') return value.startDay !== '' && value.lastDay !== '' && value.lastDay >= value.startDay;
  return value.startLocal !== '' && value.endLocal !== '' && value.endLocal > value.startLocal;
}

function blankToNull(text: string): string | null {
  const trimmed = text.trim();
  return trimmed.length === 0 ? null : trimmed;
}
