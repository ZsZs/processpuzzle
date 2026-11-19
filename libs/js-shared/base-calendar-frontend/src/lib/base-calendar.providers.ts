import type { Provider } from '@angular/core';
import { provideCalendarWidget } from './feature/calendar/calendar.widget';

/**
 * Registers the calendar widgets, so an application's pages and documents can place them. Add it to the providers
 * of the application, with the FullCalendar styles of `@processpuzzle/base-calendar/theme/pp-calendar-theme.css`.
 */
export function provideCalendarWidgets(): Provider[] {
  return [...provideCalendarWidget()];
}
