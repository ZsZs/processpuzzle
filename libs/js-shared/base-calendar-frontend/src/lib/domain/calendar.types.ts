/**
 * Frontend model of `base-calendar-api.yaml`. Plain interfaces: the payloads are JSON and are used as they arrive.
 */

export type CalendarKind = 'TEAM' | 'RESOURCE';

/** RECURRING is reserved: a calendar may allow it, but entries of that kind are refused until recurrence exists. */
export type EntryKind = 'TIMED' | 'ALL_DAY' | 'RECURRING';

export type EntryVisibility = 'PUBLIC' | 'PRIVATE';

export type AttendeeType = 'USER' | 'ROLE';

export type AttendanceResponse = 'NEEDS_ACTION' | 'ACCEPTED' | 'TENTATIVE' | 'DECLINED';

/** The answers an invitee may give; NEEDS_ACTION is only where everybody starts. */
export type AttendanceAnswerValue = 'ACCEPTED' | 'TENTATIVE' | 'DECLINED';

/** An invitation as an entry shows it. `principal` is null for anybody who may not edit the entry. */
export interface CalendarAttendee {
  type: AttendeeType;
  principal?: string | null;
  displayName: string;
  required: boolean;
  /** USER only. */
  response?: AttendanceResponse | null;
  respondedAt?: string | null;
}

export interface AttendeeInput {
  type: AttendeeType;
  principal: string;
  required: boolean;
}

/** An invitation being edited in the dialog; the display name is only for showing it. */
export interface AttendeeDraft extends AttendeeInput {
  displayName: string;
}

/** A member found by the directory search: name and subject only. */
export interface DirectoryMatch {
  sub: string;
  displayName: string;
}

export interface CalendarPermissions {
  /** May book entries and edit their own. */
  canBook: boolean;
  /** May edit and cancel anybody's entries. */
  canManage: boolean;
}

/** A released calendar as the caller sees it. The definition's role lists are never part of it. */
export interface CalendarInfo {
  key: string;
  name: string;
  description?: string | null;
  kind: CalendarKind;
  /** The organization's time zone (IANA id), in which all-day boundaries and booking rules apply. */
  effectiveTimeZone: string;
  allowedKinds?: EntryKind[];
  capacity?: number;
  slotMinutes?: number | null;
  minDurationMinutes?: number | null;
  maxDurationMinutes?: number | null;
  horizonDays?: number | null;
  permissions: CalendarPermissions;
}

/**
 * One booking. When `busy` is true the caller sees only when it takes place: title, description, location, category
 * and organizer are absent.
 */
export interface CalendarEntry {
  id: string;
  calendarKey: string;
  kind: EntryKind;
  busy: boolean;
  title?: string | null;
  description?: string | null;
  location?: string | null;
  category?: string | null;
  visibility?: EntryVisibility | null;
  /** TIMED: the first instant, ISO 8601. */
  start?: string | null;
  /** TIMED: the instant it ends, exclusive. */
  end?: string | null;
  timeZone?: string | null;
  /** ALL_DAY: the first day, `YYYY-MM-DD`. */
  startDate?: string | null;
  /** ALL_DAY: the day after the last day. */
  endDate?: string | null;
  organizerDisplayName?: string | null;
  mine?: boolean;
  canEdit?: boolean;
  /** Absent when the entry is busy. */
  attendees?: CalendarAttendee[];
  /** Whether the caller is invited, by name or through a role. */
  invited?: boolean;
  /** Whether the caller may answer the invitation. */
  canRespond?: boolean;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CalendarEntryInput {
  kind: EntryKind;
  title: string;
  description?: string | null;
  location?: string | null;
  category?: string | null;
  visibility?: EntryVisibility;
  start?: string | null;
  end?: string | null;
  timeZone?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  /** At most 50. Omit it on an update to keep the current invitees. */
  attendees?: AttendeeInput[];
  /** On an update, the version last read; a mismatch is refused with 409. */
  version?: number | null;
}

/** What a calendar shows for a window: one entry, or one occurrence of a recurring one. */
export interface Occurrence {
  entryId: string;
  calendarKey: string;
  recurrenceId?: string | null;
  allDay: boolean;
  busy: boolean;
  title?: string | null;
  location?: string | null;
  category?: string | null;
  start?: string | null;
  end?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  mine?: boolean;
  canEdit?: boolean;
  invited?: boolean;
}

/** How a failed call is told to the user. */
export type CalendarFailureKind = 'forbidden' | 'not-found' | 'conflict' | 'invalid' | 'unavailable';

/** A new entry's starting point: where the user clicked or dragged. */
export interface CalendarSlot {
  /** ISO 8601 with an offset for a timed slot, `YYYY-MM-DD` for a whole-day one. */
  start: string;
  /** Exclusive end, in the same form as `start`. */
  end: string;
  allDay: boolean;
}
