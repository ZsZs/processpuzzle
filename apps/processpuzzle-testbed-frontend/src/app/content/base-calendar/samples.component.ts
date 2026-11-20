import { Component, computed, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { CALENDAR_WIDGET } from '@processpuzzle/base-calendar';
import { HostedWidget, WidgetHostComponent } from '@processpuzzle/widgets';
import { CALENDAR_SAMPLES, CalendarSample } from './calendar-samples';

/**
 * The `calendar` widget on the calendars the testbed seeds. What it shows and allows is decided by the calendar
 * definitions and the visitor's roles — this page only chooses which calendars to put in the widget.
 */
@Component({
  selector: 'base-calendar-samples',
  standalone: true,
  imports: [TranslocoDirective, WidgetHostComponent],
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
        <pp-widget-host data-testid="calendar-sample" [widget]="widget()" />
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
  /** Placed as the widget, so the calendar's code is loaded the way a page's placement loads it. */
  protected readonly widget = computed<HostedWidget>(() => ({
    id: 'calendar-sample',
    type: CALENDAR_WIDGET,
    props: { calendarKeys: this.sample().calendarKeys, defaultView: this.sample().defaultView, height: '700px' },
  }));

  /** An id the page does not offer is ignored, so the widget never loses the calendars it shows. */
  protected pick(id: string): void {
    const chosen = CALENDAR_SAMPLES.find((candidate) => candidate.id === id);
    if (chosen) this.sample.set(chosen);
  }
}
