import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatError, MatFormField, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect, MatSelectTrigger } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';
import { decimalText, isoDateOf, toDecimal } from '@processpuzzle/util';
import { BaseEntity } from '../../base-entity/base-entity';
import { EntityLabelPipe } from '../../i18n/entity-label.pipe';
import { amountValidator, cleanAmount, isAmountText, paddedToPlaces } from './amount-text';
import { MeasureControlComponent } from './measure-control.component';
import { formatMoney } from './measure-format';
import { currenciesOf, defaultCurrencyOf } from './measure-options';
import { MEASURE_STYLES } from './measure.styles';
import { moneyOf } from './measure-values';
import { OrganizationExchangeRates } from './organization-exchange-rates';

/**
 * A `MONEY` attribute: an amount and its currency, the amount limited to the currency's minor units — two for
 * the euro, none for the yen. Changing the currency never converts the amount: a different currency is a different value,
 * not the same one in other terms.
 *
 * Where the organization has exchange rates the control offers a read-only hint: *Also show in* another currency, and the amount
 * converted at today's rate. Display only — never stored, never part of the value — and it offers only the currencies a rate
 * reaches from the one chosen.
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
        @if (hintTargets().length > 0) {
          <div class="pp-money__hint">
            <mat-form-field class="pp-measure__unit">
              <mat-label>{{ 'base_entity.measure.show_in' | transloco }}</mat-label>
              <mat-select [value]="hintCurrency()" (selectionChange)="hintCurrency.set($event.value)" data-testid="money-show-in">
                <mat-option [value]="null">—</mat-option>
                @for (code of hintTargets(); track code) {
                  <mat-option [value]="code">{{ code }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            @if (converted()) {
              <span class="pp-money__converted" data-testid="money-converted">≈ {{ converted() }}</span>
            }
          </div>
        }
      </fieldset>
    }
  `,
  styles: [MEASURE_STYLES, `.pp-money__hint { display: flex; align-items: baseline; gap: 12px; } .pp-money__converted { opacity: 0.75; }`],
})
export class MoneyComponent<Entity extends BaseEntity> extends MeasureControlComponent<Entity> implements OnInit {
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

  private readonly exchangeRates = inject(OrganizationExchangeRates);
  /** What the form holds now, as signals the hint is computed from. */
  private readonly shown = signal<{ amount: string | null; currency: string | null }>({ amount: null, currency: null });
  /** The currency the hint converts to; none until the user picks one. Never part of the value. */
  readonly hintCurrency = signal<string | null>(null);
  /** The currencies a rate reaches from the one chosen, today. */
  readonly hintTargets = computed(() => {
    const from = this.shown().currency;
    if (!from) return [];
    const rates = this.exchangeRates.rates();
    const today = isoDateOf();
    return rates.currencies().filter((code) => code !== from && this.reaches(from, code, today));
  });
  /** The amount in the hint's currency at today's rate, as text; empty while there is nothing to show. */
  readonly converted = computed(() => {
    const target = this.hintCurrency();
    const { amount, currency } = this.shown();
    const cleaned = cleanAmount(amount);
    if (!target || !currency || !cleaned || !isAmountText(cleaned) || !this.hintTargets().includes(target)) return '';
    const money = this.exchangeRates.rates().convert({ amount: decimalText(toDecimal(cleaned)), currency }, target, isoDateOf(), this.catalog);
    return formatMoney(money, this.activeLang(), this.catalog);
  });

  override ngOnInit(): void {
    super.ngOnInit();
    this.syncShown();
    this.fields.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.syncShown());
  }

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
    const currency = stored?.currency ?? defaultCurrencyOf(this.config(), offered) ?? null;
    // Shown to the minor units — the stored text is a decimal, which has lost the trailing zero of 1299.90.
    const minorUnits = currency ? this.catalog.findCurrency(currency)?.minorUnits : undefined;
    const amount = stored ? paddedToPlaces(stored.amount, minorUnits) : null;
    this.fields.reset({ amount, currency }, { emitEvent: false });
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

  private syncShown(): void {
    const { amount, currency } = this.fields.getRawValue();
    this.shown.set({ amount, currency });
  }

  private reaches(from: string, to: string, on: string): boolean {
    try {
      this.exchangeRates.rates().rate(from, to, on);
      return true;
    } catch {
      return false;
    }
  }

  /** Currency names from `Intl`; the codes are the catalog's, so well-formed, and the language is the active one. */
  private currencyNames(language: string): (code: string) => string {
    const displayNames = new Intl.DisplayNames([language], { type: 'currency' });
    return (code) => displayNames.of(code) ?? code;
  }
}
