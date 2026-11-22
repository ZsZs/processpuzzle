import { Component, computed, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatFormField, MatError, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOptgroup, MatOption, MatSelect, MatSelectTrigger } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { decimalText, toDecimal, type UnitDefinition } from '@processpuzzle/util';
import { BaseEntity } from '../../base-entity/base-entity';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { amountValidator, cleanAmount, isAmountText } from './amount-text';
import { MeasureConvertToggleComponent } from './measure-convert-toggle.component';
import { MeasureControlComponent } from './measure-control.component';
import { convertAmount, defaultUnitOf, groupByKind, quantityUnitsOf } from './measure-options';
import { MEASURE_STYLES } from './measure.styles';
import { quantityOf } from './measure-values';

/**
 * A `QUANTITY` attribute: an amount and, beside it, the unit it is in — the units of the attribute's kind, or
 * of every kind, grouped, when the attribute names none.
 *
 * Changing the unit converts the amount, so `1 m` becomes `3.28084 ft` and still measures the same; the button
 * beside the select turns that off for a user correcting a wrongly picked unit. A conversion is rounded to the
 * attribute's `scale`, six places when it sets none. The stored value is `{ amount, unit }` with the amount as
 * decimal text.
 */
@Component({
  selector: 'base-quantity',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormField,
    MatLabel,
    MatError,
    MatInput,
    MatSelect,
    MatSelectTrigger,
    MatOption,
    MatOptgroup,
    EntityLabelPipe,
    TranslocoPipe,
    MeasureConvertToggleComponent,
  ],
  template: `
    @if (config().visible) {
      <fieldset class="pp-measure" [id]="config().attrName" [formGroup]="fields">
        <legend class="pp-measure__title">{{ config().i18nKey() | ppLabel: config().label }}</legend>
        <div class="pp-measure__line">
          <mat-form-field>
            <mat-label>{{ 'base_entity.measure.amount' | transloco }}</mat-label>
            <input matInput inputmode="decimal" autocomplete="off" formControlName="amount" data-testid="quantity-amount" />
            @if (amountErrorOf(fields.controls.amount); as error) {
              <mat-error data-testid="quantity-amount-error">{{ 'base_entity.measure.error.' + error | transloco: { max: scaleLimit() } }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field class="pp-measure__unit">
            <mat-label>{{ 'base_entity.measure.unit' | transloco }}</mat-label>
            <mat-select formControlName="unit" data-testid="quantity-unit" (selectionChange)="onUnitChanged()">
              <mat-select-trigger>{{ symbolOf(fields.controls.unit.value) }}</mat-select-trigger>
              @if (groups().length > 1) {
                @for (group of groups(); track group.kind) {
                  <mat-optgroup [label]="group.name">
                    @for (unit of group.units; track unit.code) {
                      <mat-option [value]="unit.code">{{ unit.symbol }} ({{ unit.name }})</mat-option>
                    }
                  </mat-optgroup>
                }
              } @else {
                @for (unit of units(); track unit.code) {
                  <mat-option [value]="unit.code">{{ unit.symbol }} ({{ unit.name }})</mat-option>
                }
              }
            </mat-select>
          </mat-form-field>
          @if (isEditable() && groups().length === 1 && units().length > 1) {
            <base-measure-convert-toggle [converting]="convertOnUnitChange()" (toggled)="toggleConvert()" />
          }
        </div>
      </fieldset>
    }
  `,
  styles: [MEASURE_STYLES],
})
export class QuantityComponent<Entity extends BaseEntity> extends MeasureControlComponent<Entity> {
  readonly fields = new FormGroup({
    amount: new FormControl<string | null>(null),
    unit: new FormControl<string | null>(null),
  });
  readonly units = signal<UnitDefinition[]>([]);
  readonly groups = computed(() => groupByKind(this.units(), this.catalog));

  private currentUnit: string | undefined;

  /** The select's trigger shows the symbol alone; the list beside it spells the unit out. */
  symbolOf(code: string | null): string {
    return code ? (this.catalog.findUnit(code)?.symbol ?? code) : '';
  }

  /** The unit changed: the amount follows it, unless the user chose to keep the number. */
  onUnitChanged(): void {
    const from = this.currentUnit;
    const to = this.fields.controls.unit.value ?? undefined;
    this.currentUnit = to;
    const amount = cleanAmount(this.fields.controls.amount.value);
    if (this.convertOnUnitChange() && from && to && amount && isAmountText(amount)) {
      const converted = convertAmount(amount, from, to, this.config().scale, this.catalog);
      if (converted !== undefined) this.fields.controls.amount.setValue(converted, { emitEvent: false });
    }
    this.publish();
  }

  protected load(): void {
    const stored = quantityOf(this.value());
    const offered = quantityUnitsOf(this.config(), this.catalog);
    // A stored unit the attribute no longer offers is still shown — dropping it would blank a saved value.
    const storedUnit = stored ? this.catalog.findUnit(stored.unit) : undefined;
    this.units.set(storedUnit && !offered.some((unit) => unit.code === storedUnit.code) ? [...offered, storedUnit] : offered);
    const unit = stored?.unit ?? defaultUnitOf(this.config(), offered, this.catalog) ?? null;
    this.fields.reset({ amount: stored?.amount ?? null, unit }, { emitEvent: false });
    this.currentUnit = unit ?? undefined;
    this.fields.controls.amount.addValidators(amountValidator(() => this.scaleLimit()));
  }

  protected amountControls(): FormControl<string | null>[] {
    return [this.fields.controls.amount];
  }

  protected currentValue(): unknown {
    const amount = cleanAmount(this.fields.controls.amount.value);
    const unit = this.fields.controls.unit.value;
    if (!amount || !isAmountText(amount) || !unit) return null;
    return { amount: decimalText(toDecimal(amount)), unit };
  }
}
