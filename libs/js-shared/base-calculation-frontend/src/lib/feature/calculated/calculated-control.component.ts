import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslocoPipe } from '@jsverse/transloco';
import { BaseEntity, BaseFormControlComponent, EntityLabelPipe } from '@processpuzzle/base-entity';
import { catchError, debounceTime, filter, of, switchMap } from 'rxjs';
import { CALCULATED_CONTROL_I18N_SCOPE } from '../../base-calculation.i18n';
import type { CalculationOutput, EvaluationResult } from '../../domain/calculation-api';
import { CalculationApiService } from '../../domain/calculation-api.service';
import { CalculationOutputRegistry } from '../../domain/calculation-output.registry';

/** How long the form has to be quiet before the value is recomputed. */
export const CALCULATED_DEBOUNCE_MS = 400;

/**
 * The CALCULATED control: a value a released calculation derives, shown read-only with a badge naming the
 * calculation.
 *
 * **Live.** When the calculation has an entity parameter for this entity, the control listens to the form's
 * value changes and — once the form has been quiet for {@link CALCULATED_DEBOUNCE_MS} — asks the backend what the
 * *released* calculation would produce from the form's unsaved state (`{ ...entity, ...form.getRawValue() }`).
 * A newer change cancels a call still in flight. Nothing is stored; the stored value is still the calculation's
 * own run on save. Without such a parameter the stored value is simply shown.
 *
 * **Not a value editor.** Its form control is disabled — `BaseEntityFormBuilder` makes every CALCULATED control
 * so — which keeps the value out of the form's PUT. The recomputed value is written into that control without
 * an event, so it neither re-triggers the calculation nor marks the form dirty.
 *
 * **Limit.** Embedded children (order lines) are edited in their own form, so only edits of this form react
 * live; a line's change shows when this form is next rendered.
 */
@Component({
  selector: 'pp-calculated-control',
  standalone: true,
  imports: [MatFormField, MatLabel, MatHint, MatSuffix, MatInput, MatIcon, MatTooltip, TranslocoPipe, EntityLabelPipe],
  template: `
    @if (config().visible) {
      <mat-form-field floatLabel="always">
        <mat-label>{{ config().i18nKey() | ppLabel: config().label }}</mat-label>
        <input matInput readonly [value]="shown() ?? ''" [attr.data-testid]="'calculated-control-value'" />
        <mat-icon
          matSuffix
          [matTooltip]="scope + '.badge' | transloco: { calculation: output()?.calculationId ?? '' }"
          [attr.data-testid]="'calculated-control-badge'"
          >calculate</mat-icon
        >
        @if (failed()) {
          <mat-hint class="pp-calculated-control__error" [attr.data-testid]="'calculated-control-error'">{{ scope + '.error' | transloco }}</mat-hint>
        }
      </mat-form-field>
    }
  `,
  styles: [
    `
      :host {
        display: block;
      }
      mat-form-field {
        width: 100%;
      }
      .pp-calculated-control__error {
        color: #d9534f;
      }
    `,
  ],
})
export class CalculatedControlComponent<Entity extends BaseEntity> extends BaseFormControlComponent<Entity> implements OnInit {
  protected readonly scope = CALCULATED_CONTROL_I18N_SCOPE;

  private readonly api = inject(CalculationApiService);
  private readonly outputs = inject(CalculationOutputRegistry);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly output = signal<CalculationOutput | undefined>(undefined);
  protected readonly shown = signal<unknown>(undefined);
  protected readonly failed = signal(false);

  ngOnInit(): void {
    this.shown.set(this.value());
    void this.listen().catch((error) => this.logger.warn('CalculatedControlComponent failed to start', { error }));
  }

  private async listen(): Promise<void> {
    const output = await this.outputs.outputFor(this.entityName(), this.config().attrName);
    this.output.set(output);
    const parameter = output?.parameter;
    if (!output || !parameter) return;

    this.formGroup.valueChanges
      .pipe(
        debounceTime(CALCULATED_DEBOUNCE_MS),
        // A cancelled call is replaced by the newer one; an error keeps the last value and says so.
        switchMap(() => this.evaluate(output, parameter)),
        filter((result): result is EvaluationResult => result !== undefined),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((result) => this.show(result, output));
  }

  private evaluate(output: CalculationOutput, parameter: string) {
    const entity = this.entity();
    const unsaved = { [parameter]: { ...(entity as Record<string, unknown>), ...this.formGroup.getRawValue() } };
    const parameters = entity.id ? { [parameter]: entity.id } : {};
    return this.api.evaluate(output.calculationId, parameters, 'released', undefined, unsaved).pipe(
      catchError((error) => {
        this.logger.warn('CalculatedControlComponent failed to evaluate', { calculation: output.calculationId, error });
        this.failed.set(true);
        return of(undefined);
      }),
    );
  }

  private show(result: EvaluationResult, output: CalculationOutput): void {
    const values = result.values ?? {};
    if (result.error || !Object.hasOwn(values, output.decision)) {
      this.failed.set(true);
      return;
    }
    this.failed.set(false);
    const value = values[output.decision];
    this.shown.set(value);
    this.formGroup.get(this.config().attrName)?.setValue(value, { emitEvent: false });
  }
}
