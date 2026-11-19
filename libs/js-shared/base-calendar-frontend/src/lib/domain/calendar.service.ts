import { HttpClient, HttpErrorResponse, HttpParams, HttpStatusCode } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { RUNTIME_CONFIGURATION, serviceRootOf } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { CalendarEntry, CalendarEntryInput, CalendarFailureKind, CalendarInfo, Occurrence } from './calendar.types';

/** How a failed call is told to the user. 422 — a booking that breaks the calendar's rules — is `invalid`, like 400. */
export function failureKindOf(error: unknown): CalendarFailureKind {
  if (!(error instanceof HttpErrorResponse)) return 'unavailable';
  switch (error.status) {
    case HttpStatusCode.Forbidden:
    case HttpStatusCode.Unauthorized:
      return 'forbidden';
    case HttpStatusCode.NotFound:
      return 'not-found';
    case HttpStatusCode.Conflict:
      return 'conflict';
    case HttpStatusCode.BadRequest:
    case HttpStatusCode.UnprocessableEntity:
      return 'invalid';
    default:
      return 'unavailable';
  }
}

/**
 * The server's own explanation of a refusal (`errorText` of the error body), when there is one. A 422 lists every
 * rule the booking broke; showing that beats a generic "invalid".
 */
export function failureTextOf(error: unknown): string | undefined {
  if (!(error instanceof HttpErrorResponse)) return undefined;
  const text = (error.error as { errorText?: unknown } | null)?.errorText;
  return typeof text === 'string' && text.length > 0 ? text : undefined;
}

/**
 * The REST half of the calendar, against `base-calendar-api.yaml`.
 *
 * Goes through the application's `HttpClient`, so its interceptors attach the token when there is one; the read calls
 * also work without a token, on a public calendar. The organization is part of the configured service root
 * (`CALENDAR_SERVICE_ROOT`, which falls back to `BACKEND_SERVICE_ROOT`), as for every other feature.
 */
@Injectable({ providedIn: 'root' })
export class CalendarService {
  private readonly http = inject(HttpClient);
  private readonly root = serviceRootOf(inject(RUNTIME_CONFIGURATION), 'CALENDAR_SERVICE_ROOT');
  private readonly base = `${this.root}/calendar`;

  getCalendar(calendarKey: string): Promise<CalendarInfo> {
    return firstValueFrom(this.http.get<CalendarInfo>(`${this.base}/calendars/${encodeURIComponent(calendarKey)}`));
  }

  /** What the calendars show in `[from, to)`. A calendar the caller may not read is left out by the server. */
  listOccurrences(calendarKeys: readonly string[], from: string, to: string): Promise<Occurrence[]> {
    const params = new HttpParams().set('calendars', calendarKeys.join(',')).set('from', from).set('to', to);
    return firstValueFrom(this.http.get<Occurrence[]>(`${this.base}/occurrences`, { params }));
  }

  getEntry(calendarKey: string, entryId: string): Promise<CalendarEntry> {
    return firstValueFrom(this.http.get<CalendarEntry>(this.entryUrl(calendarKey, entryId)));
  }

  bookEntry(calendarKey: string, input: CalendarEntryInput): Promise<CalendarEntry> {
    return firstValueFrom(this.http.post<CalendarEntry>(`${this.base}/calendars/${encodeURIComponent(calendarKey)}/entries`, input));
  }

  updateEntry(calendarKey: string, entryId: string, input: CalendarEntryInput): Promise<CalendarEntry> {
    return firstValueFrom(this.http.put<CalendarEntry>(this.entryUrl(calendarKey, entryId), input));
  }

  async cancelEntry(calendarKey: string, entryId: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(this.entryUrl(calendarKey, entryId)));
  }

  private entryUrl(calendarKey: string, entryId: string): string {
    return `${this.base}/calendars/${encodeURIComponent(calendarKey)}/entries/${encodeURIComponent(entryId)}`;
  }
}
