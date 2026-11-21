import { Component, computed, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect, MatSelectTrigger } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { decimalText, toDecimal } from '@processpuzzle/util';
import { BaseEntity } from '../../base-entity/base-entity';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { amountValidator, cleanAmount, isAmountText } from './amount-text';
import { MeasureControlComponent } from './measure-control.component';
import { currenciesOf, defaultCurrencyOf } from './measure-options';
import { MEASURE_STYLES } from './measure.styles';
import { moneyOf } from './measure-values';

/**
 * A `MONEY` attribute: an amount and its currency, the amount limited to the currency's minor units — two for
 * the euro, none for the yen. Changing the currency never converts the amount: there are no exchange rates, so a
 * different currency is a different value, not the same one in other terms.
 *
 * The stored value is `{ amount, currency }` with the amount as decimal text and the currency an ISO 4217 code.
 */
@Component({
  selector: 'base-money',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormField, MatLabel, MatError, MatInput, MatSelect, MatSelectTrigger, MatOption, EntityLabelPipe, TranslocoPipe],
  template: `
    @if (config().visible) {
      <fieldset class="pp-measure" [id]="config().attrName" [formGroup]="fields">
        <legend class="pp-measure__title">{{ config().i18nKey() | ppLabel: config().label }}</legend>
        <div class="pp-measure__line">
          <mat-form-field>
            <mat-label>{{ 'base_entity.measure.amount' | transloco }}</mat-label>
            <input matInput inputmode="decimal" autocomplete="off" formControlName="amount" data-testid="money-amount" />
            @if (amountErrorOf(fields.controls.amount); as error) {
              <mat-error data-testid="money-amount-error">{{ 'base_entity.measure.error.' + error | transloco: { max: scaleLimit() } }}</mat-error>
            }
          </mat-form-field>
          <mat-form-field class="pp-measure__unit">
            <mat-label>{{ 'base_entity.measure.currency' | transloco }}</mat-label>
            <mat-select formControlName="currency" data-testid="money-currency" (selectionChange)="onCurrencyChanged()">
              <mat-select-trigger>{{ fields.controls.currency.value }}</mat-select-trigger>
              @for (currency of currencyOptions(); track currency.code) {
                <mat-option [value]="currency.code">{{ currency.code }} — {{ currency.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
        </div>
      </fieldset>
    }
  `,
  styles: [MEASURE_STYLES],
})
export class MoneyComponent<Entity extends BaseEntity> extends MeasureControlComponent<Entity> {
  readonly fields = new FormGroup({
    amount: new FormControl<string | null>(null),
    currency: new FormControl<string | null>(null),
  });
  readonly currencies = signal<string[]>([]);
  /** The offered currencies with their names in the active language; the code stands in for one `Intl` has no name for. */
  readonly currencyOptions = computed(() => {
    const names = this.currencyNames(this.activeLang());
    return this.currencies().map((code) => ({ code, name: names(code) }));
  });

  /** A currency's minor units are the amount's decimal places. */
  override scaleLimit(): number | undefined {
    const currency = this.fields.controls.currency.value;
    return currency ? this.catalog.findCurrency(currency)?.minorUnits : undefined;
  }

  /** The limit moved with the currency, so the amount is checked again. */
  onCurrencyChanged(): void {
    this.fields.controls.amount.updateValueAndValidity();
  }

  protected load(): void {
    const stored = moneyOf(this.value());
    const offered = currenciesOf(this.config(), this.catalog);
    // A stored currency the attribute no longer offers is still shown, as in `QuantityComponent`.
    this.currencies.set(stored && !offered.includes(stored.currency) && this.catalog.findCurrency(stored.currency) ? [...offered, stored.currency] : offered);
    this.fields.reset({ amount: stored?.amount ?? null, currency: stored?.currency ?? defaultCurrencyOf(this.config(), offered) ?? null }, { emitEvent: false });
    this.fields.controls.amount.addValidators(amountValidator(() => this.scaleLimit()));
  }

  protected amountControls(): FormControl<string | null>[] {
    return [this.fields.controls.amount];
  }

  protected currentValue(): unknown {
    const amount = cleanAmount(this.fields.controls.amount.value);
    const currency = this.fields.controls.currency.value;
    if (!amount || !isAmountText(amount) || !currency) return null;
    return { amount: decimalText(toDecimal(amount)), currency };
  }

  /** Currency names from `Intl`; the codes are the catalog's, so well-formed, and the language is the active one. */
  private currencyNames(language: string): (code: string) => string {
    const displayNames = new Intl.DisplayNames([language], { type: 'currency' });
    return (code) => displayNames.of(code) ?? code;
  }
}
