import { Component, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { CalendarComponent } from '@processpuzzle/base-calendar';
import { CALENDAR_SAMPLES, CalendarSample } from './calendar-samples';

/**
 * The `calendar` widget on the calendars the testbed seeds. What it shows and allows is decided by the calendar
 * definitions and the visitor's roles — this page only chooses which calendars to put in the widget.
 */
@Component({
  selector: 'base-calendar-samples',
  standalone: true,
  imports: [TranslocoDirective, CalendarComponent],
  template: `
    <ng-container *transloco="let t; prefix: 'base-calendar'">
      <section class="samples">
        <p>{{ t('samples_desc_1') }}</p>
        <p>{{ t('samples_desc_2') }}</p>
        <div class="fields">
          <label>
            {{ t('sample_label') }}
            <select data-testid="calendar-sample-select" (change)="pick($any($event.target).value)">
              @for (option of samples; track option.id) {
                <option [value]="option.id" [selected]="option.id === sample().id">{{ t(option.labelKey) }}</option>
              }
            </select>
          </label>
        </div>
        <pp-calendar data-testid="calendar-sample" [calendarKeys]="sample().calendarKeys" [defaultView]="sample().defaultView" height="700px" />
      </section>
    </ng-container>
  `,
  styles: `
    .samples {
      padding: 16px;
      max-width: 1100px;
    }
    .fields {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      margin: 12px 0;
    }
    .fields label {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .fields select {
      min-width: 22rem;
      padding: 6px 8px;
      font: inherit;
    }
  `,
})
export class SamplesComponent {
  protected readonly samples = CALENDAR_SAMPLES;
  protected readonly sample = signal<CalendarSample>(CALENDAR_SAMPLES[0]);

  /** An id the page does not offer is ignored, so the widget never loses the calendars it shows. */
  protected pick(id: string): void {
    const chosen = CALENDAR_SAMPLES.find((candidate) => candidate.id === id);
    if (chosen) this.sample.set(chosen);
  }
}
