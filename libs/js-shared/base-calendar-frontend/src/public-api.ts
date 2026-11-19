/*
 * Public API Surface of @processpuzzle/base-calendar
 */
export { BASE_CALENDAR_TRANSLOCO_ALIAS, BASE_CALENDAR_TRANSLOCO_SCOPE } from './lib/base-calendar.i18n';
export { provideCalendarWidgets } from './lib/base-calendar.providers';

export { CalendarService, failureKindOf, failureTextOf } from './lib/domain/calendar.service';
export { CalendarStore, KindChangeError, type Reschedule } from './lib/domain/calendar.store';
export type {
  CalendarEntry,
  CalendarEntryInput,
  CalendarFailureKind,
  CalendarInfo,
  CalendarKind,
  CalendarPermissions,
  CalendarSlot,
  EntryKind,
  EntryVisibility,
  Occurrence,
} from './lib/domain/calendar.types';

export { CalendarComponent, type CalendarViewName } from './lib/feature/calendar/calendar.component';
export { CALENDAR_WIDGET, CALENDAR_WIDGET_REGISTRATION, provideCalendarWidget } from './lib/feature/calendar/calendar.widget';
export { EntryDialogComponent, type EntryDialogData, type EntryDialogResult } from './lib/feature/entry-dialog/entry-dialog.component';
