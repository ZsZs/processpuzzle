import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { TranslocoPipe } from '@jsverse/transloco';
import { isComplete, ProblemResolution } from './release';

/**
 * One way to resolve a release problem: what the migration step would do, the one value it still needs — a choice
 * from a list, a yes/no, a number, a date or a text — and Apply, which adds the step to the pending ones.
 */
@Component({
  selector: 'pp-problem-resolution',
  standalone: true,
  imports: [TranslocoPipe, MatButton, MatCheckbox],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="resolution" [attr.data-testid]="'resolution-' + resolution().step.operation">
      <span class="resolution__label">{{ 'base_release.release.resolve_' + resolution().step.operation | transloco }}</span>
      @if (resolution().input) {
        @if (resolution().choices?.length) {
          <select data-testid="resolution-choice" [value]="value() ?? ''" (change)="choose($event)">
            <option value="" disabled>{{ 'base_release.release.resolve_choose' | transloco }}</option>
            @for (choice of resolution().choices; track choice) {
              <option [value]="choice">{{ choice }}</option>
            }
          </select>
        } @else if (resolution().inputKind === 'boolean') {
          <mat-checkbox data-testid="resolution-flag" [checked]="value() === true" (change)="value.set($event.checked)" />
        } @else {
          <input data-testid="resolution-value" [type]="htmlType()" [value]="value() ?? ''" (input)="type($event)" />
        }
      }
      <button mat-stroked-button type="button" data-testid="resolution-apply" [disabled]="disabled() || !complete()" (click)="applied.emit(value() ?? false)">
        {{ 'base_release.release.resolve_apply' | transloco }}
      </button>
    </div>
  `,
  styles: `
    .resolution {
      display: flex;
      align-items: center;
      gap: 8px;
      margin: 4px 0 8px 16px;
      color: #333333;
    }
  `,
})
export class ProblemResolutionComponent {
  readonly resolution = input.required<ProblemResolution>();
  /** While the panel is busy, nothing can be applied. */
  readonly disabled = input(false);
  /** The value the designer gave, as the input holds it. */
  readonly applied = output<string | boolean>();

  protected readonly value = signal<string | boolean | undefined>(undefined);
  protected readonly complete = computed(() => isComplete(this.resolution(), this.value()));
  protected readonly htmlType = computed(() => {
    switch (this.resolution().inputKind) {
      case 'number':
        return 'number';
      case 'date':
        return 'date';
      case 'dateTime':
        return 'datetime-local';
      default:
        return 'text';
    }
  });

  protected choose(event: Event): void {
    this.value.set((event.target as HTMLSelectElement).value);
  }

  protected type(event: Event): void {
    this.value.set((event.target as HTMLInputElement).value);
  }
}
