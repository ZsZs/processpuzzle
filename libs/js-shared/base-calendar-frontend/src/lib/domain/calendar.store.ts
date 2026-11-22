import { computed, inject, Injectable, signal } from '@angular/core';
import { CalendarService, failureKindOf } from './calendar.service';
import { AttendanceAnswerValue, CalendarEntry, CalendarEntryInput, CalendarFailureKind, CalendarInfo, DirectoryMatch, Occurrence } from './calendar.types';
import { templateKindOf } from './entry-form';

function occurrenceId(occurrence: Occurrence): string {
  return `${occurrence.calendarKey}:${occurrence.entryId}:${occurrence.recurrenceId ?? ''}`;
}

/** The new times of an entry, as a drag or a resize produces them. */
export interface Reschedule {
  allDay: boolean;
  /** TIMED: ISO instants. */
  start?: string;
  end?: string;
  /** ALL_DAY: `YYYY-MM-DD`, the end exclusive. */
  startDate?: string;
  endDate?: string;
}

/** A drag moved a timed entry onto the all-day row, or the other way round; the kind of an entry cannot change. */
export class KindChangeError extends Error {
  constructor() {
    super('The kind of an entry cannot change');
  }
}

/**
 * What one `pp-calendar` knows: the calendars it shows, as the caller sees them, and the calls that change entries.
 * Provided per widget instance, so two calendars on one page do not share state.
 *
 * It holds no occurrences: FullCalendar asks for a window when it needs one, and keeping a second copy of what it
 * already caches would only let the two disagree.
 */
@Injectable()
export class CalendarStore {
  private readonly service = inject(CalendarService);

  private readonly _calendars = signal<readonly CalendarInfo[]>([]);
  private readonly _status = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  private readonly _failure = signal<CalendarFailureKind | null>(null);

  /** The calendars the caller may read, in the order they were asked for. */
  readonly calendars = this._calendars.asReadonly();
  readonly status = this._status.asReadonly();
  /** Why no calendar could be shown, when none could. */
  readonly failure = this._failure.asReadonly();

  /** The organization's zone, the same for every calendar of an organization; UTC until a calendar is loaded. */
  readonly timeZone = computed(() => this._calendars()[0]?.effectiveTimeZone ?? 'UTC');
  /** Whether the caller may book on at least one of the calendars shown. */
  readonly canBook = computed(() => this._calendars().some((calendar) => calendar.permissions.canBook));

  calendarOf(key: string): CalendarInfo | undefined {
    return this._calendars().find((calendar) => calendar.key === key);
  }

  /**
   * Loads the calendars by key. One the caller may not read (404) is left out, as the server leaves it out of the
   * occurrences; only when none can be shown is that an error.
   */
  async loadCalendars(keys: readonly string[]): Promise<void> {
    if (keys.length === 0) {
      this._calendars.set([]);
      this._failure.set(null);
      this._status.set('idle');
      return;
    }
    this._status.set('loading');
    const results = await Promise.allSettled([...new Set(keys)].map((key) => this.service.getCalendar(key)));
    const loaded = results.flatMap((result) => (result.status === 'fulfilled' ? [result.value] : []));
    this._calendars.set(loaded);
    if (loaded.length > 0) {
      this._failure.set(null);
      this._status.set('ready');
      return;
    }
    const firstFailure = results.find((result): result is PromiseRejectedResult => result.status === 'rejected');
    this._failure.set(failureKindOf(firstFailure?.reason));
    this._status.set('error');
  }

  /**
   * What the shown calendars hold in `[from, to)`. With `includeMine` the caller's own entries and invitations, from
   * every calendar they may read, are added; an occurrence that is in both lists is shown once.
   */
  async loadOccurrences(from: string, to: string, includeMine = false): Promise<Occurrence[]> {
    const keys = this._calendars().map((calendar) => calendar.key);
    const [shown, mine] = await Promise.all([
      keys.length === 0 ? Promise.resolve([]) : this.service.listOccurrences(keys, from, to),
      includeMine ? this.service.listMyOccurrences(from, to) : Promise.resolve([]),
    ]);
    const seen = new Set(shown.map(occurrenceId));
    return [...shown, ...mine.filter((occurrence) => !seen.has(occurrenceId(occurrence)))];
  }

  searchDirectory(q: string): Promise<DirectoryMatch[]> {
    return this.service.searchDirectory(q);
  }

  roleCatalog(): Promise<{ name: string; description?: string }[]> {
    return this.service.roleCatalog();
  }

  answer(calendarKey: string, entryId: string, response: AttendanceAnswerValue): Promise<CalendarEntry> {
    return this.service.answerInvitation(calendarKey, entryId, response);
  }

  getEntry(calendarKey: string, entryId: string): Promise<CalendarEntry> {
    return this.service.getEntry(calendarKey, entryId);
  }

  book(calendarKey: string, input: CalendarEntryInput): Promise<CalendarEntry> {
    return this.service.bookEntry(calendarKey, input);
  }

  update(calendarKey: string, entryId: string, input: CalendarEntryInput): Promise<CalendarEntry> {
    return this.service.updateEntry(calendarKey, entryId, input);
  }

  cancel(calendarKey: string, entryId: string): Promise<void> {
    return this.service.cancelEntry(calendarKey, entryId);
  }

  /**
   * Moves or resizes an entry. The update replaces the whole entry, and an occurrence does not carry its description,
   * so the entry is read first and written back with the new times and the version it was read at — a concurrent
   * edit is then refused rather than overwritten.
   *
   * @throws KindChangeError when the new times are of the other kind than the entry
   */
  async reschedule(calendarKey: string, entryId: string, times: Reschedule): Promise<CalendarEntry> {
    const entry = await this.service.getEntry(calendarKey, entryId);
    if (times.allDay !== (templateKindOf(entry) === 'ALL_DAY')) {
      throw new KindChangeError();
    }
    const input: CalendarEntryInput = {
      kind: entry.kind,
      title: entry.title ?? '',
      description: entry.description ?? null,
      location: entry.location ?? null,
      category: entry.category ?? null,
      visibility: entry.visibility ?? 'PUBLIC',
      version: entry.version ?? null,
      ...(entry.recurrence ? { recurrence: entry.recurrence } : {}),
      ...(times.allDay ? { startDate: times.startDate, endDate: times.endDate } : { start: times.start, end: times.end, timeZone: entry.timeZone ?? null }),
    };
    return this.service.updateEntry(calendarKey, entryId, input);
  }
}
