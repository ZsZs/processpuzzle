import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Type } from '@angular/core';
import { By } from '@angular/platform-browser';
import { CALENDAR_WIDGET_REGISTRATION, CalendarService, provideCalendarWidget, type CalendarComponent } from '@processpuzzle/base-calendar';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { loadWidgetComponent } from '@processpuzzle/widgets';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SamplesComponent } from './samples.component';

/**
 * The page is tested with the real widget, whose calendar service is faked to answer 404: the widget then shows its
 * "not available" line and draws no calendar, so nothing heavy runs, and the page keeps its compiled template.
 * Replacing the widget with a stub (`overrideComponent`) recompiles the page and loses the coverage of that template.
 */
describe('SamplesComponent', () => {
  const translations = { en: { 'base-calendar.sample_public': 'Public events', 'base-calendar.sample_team': 'Team calendar', 'base-calendar.sample_overlay': 'Team and room' } };
  let getCalendar: ReturnType<typeof vi.fn>;
  let fixture: ComponentFixture<SamplesComponent>;
  let calendarClass: Type<unknown>;

  const widget = () => fixture.debugElement.query(By.directive(calendarClass)).componentInstance as CalendarComponent;
  const select = () => fixture.nativeElement.querySelector('[data-testid="calendar-sample-select"]') as HTMLSelectElement;
  const options = () => Array.from(select().querySelectorAll('option')) as HTMLOptionElement[];

  function pick(id: string) {
    select().value = id;
    select().dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  beforeEach(async () => {
    // The widget loads its class when it is first placed; the page's calendar is found once that has happened.
    calendarClass = await loadWidgetComponent(CALENDAR_WIDGET_REGISTRATION);
    getCalendar = vi.fn().mockRejectedValue(new HttpErrorResponse({ status: 404 }));
    TestBed.configureTestingModule({
      imports: [SamplesComponent],
      providers: [provideTranslocoTesting({ translations }), provideCalendarWidget(), { provide: CalendarService, useValue: { getCalendar } }],
    });
    fixture = TestBed.createComponent(SamplesComponent);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
  });

  it('starts with the public calendar in the month view, which a visitor can read without signing in', () => {
    expect(widget().calendarKeys()).toEqual(['public-events']);
    expect(widget().defaultView()).toBe('month');
    expect(widget().height()).toBe('700px');
  });

  it('asks the backend for the calendars it shows', () => {
    expect(getCalendar).toHaveBeenCalledWith('public-events');
  });

  it('offers every sample in the drop-down, under its translated name, with the first selected', () => {
    expect(options().map((option) => option.value)).toEqual(['public', 'team', 'overlay']);
    expect(options().map((option) => option.textContent?.trim())).toEqual(['Public events', 'Team calendar', 'Team and room']);
    expect(options().map((option) => option.selected)).toEqual([true, false, false]);
  });

  it('puts the chosen calendars into the widget', () => {
    pick('overlay');

    expect(widget().calendarKeys()).toEqual(['team-calendar', 'meeting-room-a']);
    expect(widget().defaultView()).toBe('week');
    expect(options().map((option) => option.selected)).toEqual([false, false, true]);
  });

  it('ignores an id the page does not offer, so the widget keeps what it shows', () => {
    pick('team');
    pick('nonsense');

    expect(widget().calendarKeys()).toEqual(['team-calendar']);
  });
});
