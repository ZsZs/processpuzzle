import { Component, computed, signal } from '@angular/core';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, ValidationErrors } from '@angular/forms';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect, MatSelectTrigger } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { decimalText, toDecimal, type DimensionsValue, type UnitDefinition } from '@processpuzzle/util';
import { BaseEntity } from '../../base-entity/base-entity';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { amountValidator, cleanAmount, isAmountText } from './amount-text';
import { MeasureConvertToggleComponent } from './measure-convert-toggle.component';
import { MeasureControlComponent } from './measure-control.component';
import { convertAmount, defaultUnitOf, dimensionUnitsOf } from './measure-options';
import { MEASURE_STYLES } from './measure.styles';
import { dimensionsOf } from './measure-values';

type Axis = 'width' | 'height' | 'depth';

/**
 * A `DIMENSIONS` attribute: two (`width × height`) or three (`width × height × depth`) lengths and one unit for
 * all of them. Changing the unit converts every axis, with the same switch as `QUANTITY` to keep the numbers.
 *
 * Either every axis has an amount or none does — a width with no height measures nothing — and the incomplete
 * state blocks Save. The stored value is `{ width, height, depth?, unit }` with the amounts as decimal text.
 */
@Component({
  selector: 'base-dimensions',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatError, MatInput, MatSelect, MatSelectTrigger, MatOption, EntityLabelPipe, TranslocoPipe, MeasureConvertToggleComponent],
  template: `
    @if (config().visible) {
      <fieldset class="pp-measure" [id]="config().attrName" [formGroup]="fields">
        <legend class="pp-measure__title">{{ config().i18nKey() | ppLabel: config().label }}</legend>
        <div class="pp-measure__line">
          @for (axis of axes(); track axis) {
            <mat-form-field>
              <mat-label>{{ 'base_entity.measure.' + axis | transloco }}</mat-label>
              <input matInput inputmode="decimal" autocomplete="off" [formControlName]="axis" [attr.data-testid]="'dimensions-' + axis" />
              @if (amountErrorOf(fields.controls[axis]); as error) {
                <mat-error [attr.data-testid]="'dimensions-' + axis + '-error'">{{ 'base_entity.measure.error.' + error | transloco: { max: scaleLimit() } }}</mat-error>
              }
            </mat-form-field>
          }
          <mat-form-field class="pp-measure__unit">
            <mat-label>{{ 'base_entity.measure.unit' | transloco }}</mat-label>
            <mat-select formControlName="unit" data-testid="dimensions-unit" (selectionChange)="onUnitChanged()">
              <mat-select-trigger>{{ symbolOf(fields.controls.unit.value) }}</mat-select-trigger>
              @for (unit of units(); track unit.code) {
                <mat-option [value]="unit.code">{{ unit.symbol }} ({{ unit.name }})</mat-option>
              }
            </mat-select>
          </mat-form-field>
          @if (isEditable() && units().length > 1) {
            <base-measure-convert-toggle [converting]="convertOnUnitChange()" (toggled)="toggleConvert()" />
          }
        </div>
        @if (fields.hasError('incomplete') && fields.touched) {
          <p class="pp-measure__error" data-testid="dimensions-incomplete">{{ 'base_entity.measure.error.incomplete' | transloco }}</p>
        }
      </fieldset>
    }
  `,
  styles: [MEASURE_STYLES],
})
export class DimensionsComponent<Entity extends BaseEntity> extends MeasureControlComponent<Entity> {
  readonly fields = new FormGroup({
    width: new FormControl<string | null>(null),
    height: new FormControl<string | null>(null),
    depth: new FormControl<string | null>(null),
    unit: new FormControl<string | null>(null),
  });
  readonly units = signal<UnitDefinition[]>([]);
  readonly axes = computed<Axis[]>(() => (this.config().dimensionCount === 3 ? ['width', 'height', 'depth'] : ['width', 'height']));

  private currentUnit: string | undefined;

  symbolOf(code: string | null): string {
    return code ? (this.catalog.findUnit(code)?.symbol ?? code) : '';
  }

  /** The unit changed: every axis follows it, unless the user chose to keep the numbers. */
  onUnitChanged(): void {
    const from = this.currentUnit;
    const to = this.fields.controls.unit.value ?? undefined;
    this.currentUnit = to;
    if (this.convertOnUnitChange() && from && to) {
      for (const axis of this.axes()) {
        const control = this.fields.controls[axis];
        const amount = cleanAmount(control.value);
        if (!amount || !isAmountText(amount)) continue;
        const converted = convertAmount(amount, from, to, this.config().scale, this.catalog);
        if (converted !== undefined) control.setValue(converted, { emitEvent: false });
      }
    }
    this.publish();
  }

  protected load(): void {
    const stored = dimensionsOf(this.value());
    const offered = dimensionUnitsOf(this.config(), this.catalog);
    const storedUnit = stored ? this.catalog.findUnit(stored.unit) : undefined;
    this.units.set(storedUnit?.kind === 'LENGTH' && !offered.some((unit) => unit.code === storedUnit.code) ? [...offered, storedUnit] : offered);
    const unit = stored?.unit ?? defaultUnitOf(this.config(), offered, this.catalog) ?? null;
    this.fields.reset({ width: stored?.width ?? null, height: stored?.height ?? null, depth: stored?.depth ?? null, unit }, { emitEvent: false });
    this.currentUnit = unit ?? undefined;
    for (const axis of ['width', 'height', 'depth'] as const) this.fields.controls[axis].addValidators(amountValidator(() => this.scaleLimit()));
    // Added here, not at construction: the validator reads the attribute's `dimensionCount`, which is bound later.
    this.fields.addValidators((group) => this.incompleteValidator(group));
  }

  protected amountControls(): AbstractControl[] {
    return this.axes().map((axis) => this.fields.controls[axis]);
  }

  protected currentValue(): DimensionsValue | null {
    const unit = this.fields.controls.unit.value;
    const amounts = this.axes().map((axis) => cleanAmount(this.fields.controls[axis].value));
    if (!unit || amounts.some((amount) => !amount || !isAmountText(amount))) return null;
    const [width, height, depth] = amounts.map((amount) => decimalText(toDecimal(amount)));
    return depth === undefined ? { width, height, unit } : { width, height, depth, unit };
  }

  /** Some axes filled and others empty: a width without a height is not a size. */
  private incompleteValidator(group: AbstractControl): ValidationErrors | null {
    const filled = this.axes().map((axis) => !!cleanAmount(group.get(axis)?.value as string | null));
    return filled.some(Boolean) && !filled.every(Boolean) ? { incomplete: true } : null;
  }
}
