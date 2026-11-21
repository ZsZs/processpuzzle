import { Component, input, output } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * The button beside a unit select that says what changing the unit does: convert the amount (on, the default) or
 * keep the number as typed (off) — for the user who picked the wrong unit and is correcting it.
 */
@Component({
  selector: 'base-measure-convert-toggle',
  standalone: true,
  imports: [MatIconButton, MatIcon, MatTooltip, TranslocoPipe],
  template: `
    <button
      mat-icon-button
      type="button"
      class="pp-measure-convert"
      data-testid="measure-convert-toggle"
      [attr.aria-pressed]="converting()"
      [attr.aria-label]="(converting() ? 'base_entity.measure.convert_on' : 'base_entity.measure.convert_off') | transloco"
      [matTooltip]="(converting() ? 'base_entity.measure.convert_on' : 'base_entity.measure.convert_off') | transloco"
      (click)="toggled.emit()"
    >
      <mat-icon>{{ converting() ? 'sync' : 'sync_disabled' }}</mat-icon>
    </button>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
        margin-top: 8px;
      }
    `,
  ],
})
export class MeasureConvertToggleComponent {
  readonly converting = input.required<boolean>();
  readonly toggled = output<void>();
}
