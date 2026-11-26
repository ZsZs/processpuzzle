import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltip } from '@angular/material/tooltip';
import { provideTranslocoScope, TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { NGXLogger } from 'ngx-logging-kit';
import { AI_ASSIST_CLIENT, AiAssistRequest, AiAssistResult } from './ai-assist';

/**
 * The one button of the AI assist: an icon beside a field that is enabled once the description has text, and that writes the
 * generated value into the field when clicked. It is not shown at all unless an {@link AI_ASSIST_CLIENT} is provided and
 * reports the task available.
 *
 * The host owns the field: the button hands it the value through {@link generated}. When it replaces something, or the result
 * is not clean, a snackbar says so, and offers to bring back what was there; undoing emits the earlier value the same way.
 * A failed request is left to the application's error handling, which already tells the user about an HTTP error.
 */
@Component({
  selector: 'base-ai-assist-button',
  standalone: true,
  imports: [MatIconButton, MatIcon, MatProgressSpinner, MatTooltip, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'base_entity', alias: 'base_entity' })],
  styles: [
    `
      :host {
        display: inline-flex;
      }
      mat-spinner {
        margin: 0 auto;
      }
    `,
  ],
  template: `
    @if (available()) {
      <ng-container *transloco="let t">
        <button
          type="button"
          mat-icon-button
          data-testid="ai-assist-button"
          [disabled]="!enabled()"
          [attr.aria-label]="t('base_entity.ai_assist.tooltip')"
          [matTooltip]="t('base_entity.ai_assist.tooltip')"
          (click)="generate()"
        >
          @if (busy()) {
            <mat-spinner diameter="20" />
          } @else {
            <mat-icon>auto_awesome</mat-icon>
          }
        </button>
      </ng-container>
    }
  `,
})
export class AiAssistButtonComponent {
  /** The assist task to run. */
  readonly task = input.required<string>();
  /** The description the value is written from; the button is disabled while it is blank. */
  readonly text = input.required<string>();
  /** The task's other form values, by name. */
  readonly parameters = input<Record<string, string>>({});
  /** What the field holds now. */
  readonly current = input<string>('');
  /** The value for the field: the generated one, or the earlier one when the user undoes it. */
  readonly generated = output<string>();

  protected readonly busy = signal(false);

  private readonly client = inject(AI_ASSIST_CLIENT, { optional: true });
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly logger = inject(NGXLogger);

  protected readonly available = computed(() => this.client?.isAvailable(this.task())() ?? false);
  protected readonly enabled = computed(() => this.text().trim().length > 0 && !this.busy());

  async generate(): Promise<void> {
    if (!this.client || !this.enabled()) {
      return;
    }
    const previous = this.current();
    const request: AiAssistRequest = { text: this.text().trim(), parameters: this.parameters(), current: previous };
    this.busy.set(true);
    try {
      this.report(await this.client.run(this.task(), request), previous);
    } catch (error) {
      this.logger.warn('The AI assist request failed.', error instanceof HttpErrorResponse ? error.status : error);
    } finally {
      this.busy.set(false);
    }
  }

  private report(result: AiAssistResult, previous: string): void {
    if (!result.candidate) {
      this.snackBar.open(this.transloco.translate('base_entity.ai_assist.empty'), undefined, { duration: 6000 });
      return;
    }
    this.generated.emit(result.candidate);
    const problem = result.issues.find((issue) => issue.severity === 'ERROR');
    if (problem) {
      this.offerUndo(this.transloco.translate('base_entity.ai_assist.issue', { issue: problem.text }), previous, 10000);
    } else if (previous.trim()) {
      this.offerUndo(this.transloco.translate('base_entity.ai_assist.replaced'), previous, 8000);
    }
  }

  private offerUndo(message: string, previous: string, duration: number): void {
    if (!previous.trim()) {
      this.snackBar.open(message, undefined, { duration });
      return;
    }
    this.snackBar
      .open(message, this.transloco.translate('base_entity.ai_assist.undo'), { duration })
      .onAction()
      .subscribe(() => this.generated.emit(previous));
  }
}
