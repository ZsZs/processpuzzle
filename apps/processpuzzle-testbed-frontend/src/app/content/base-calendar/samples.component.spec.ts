import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CalendarComponent } from '@processpuzzle/base-calendar';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { beforeEach, describe, expect, it } from 'vitest';
import { SamplesComponent } from './samples.component';

/** The real widget talks to the backend; the page only has to hand it the right inputs. */
@Component({ selector: 'pp-calendar', standalone: true, template: '' })
class CalendarStub {
  readonly calendarKeys = input<string[]>([]);
  readonly defaultView = input<string>('week');
  readonly height = input<string>('auto');
}

describe('SamplesComponent', () => {
  const translations = { en: { 'base-calendar.sample_public': 'Public events', 'base-calendar.sample_team': 'Team calendar', 'base-calendar.sample_overlay': 'Team and room' } };
  let fixture: ComponentFixture<SamplesComponent>;

  const stub = () => fixture.debugElement.query(By.directive(CalendarStub)).componentInstance as CalendarStub;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [SamplesComponent], providers: [provideTranslocoTesting({ translations })] });
    TestBed.overrideComponent(SamplesComponent, { remove: { imports: [CalendarComponent] }, add: { imports: [CalendarStub] } });
    fixture = TestBed.createComponent(SamplesComponent);
    fixture.detectChanges();
  });

  it('starts with the public calendar in the month view, which a visitor can read without signing in', () => {
    expect(stub().calendarKeys()).toEqual(['public-events']);
    expect(stub().defaultView()).toBe('month');
    expect(stub().height()).toBe('700px');
  });

  it('offers every sample in the drop-down', () => {
    const options = Array.from(fixture.nativeElement.querySelectorAll('[data-testid="calendar-sample-select"] option')) as HTMLOptionElement[];

    expect(options.map((option) => option.value)).toEqual(['public', 'team', 'overlay']);
  });

  it('puts the chosen calendars into the widget', () => {
    const select = fixture.nativeElement.querySelector('[data-testid="calendar-sample-select"]') as HTMLSelectElement;

    select.value = 'overlay';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(stub().calendarKeys()).toEqual(['team-calendar', 'meeting-room-a']);
    expect(stub().defaultView()).toBe('week');
  });
});
