import { signal } from '@angular/core';
import { ExchangeRates, type ExchangeRateData } from '@processpuzzle/util';
import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { MoneyComponent } from './money.component';
import { OrganizationExchangeRates } from './organization-exchange-rates';

interface SetupOptions {
  value?: unknown;
  init?: Partial<BaseEntityAttrDescriptor>;
  /** The organization's exchange rates; none when absent. */
  rates?: ExchangeRateData[];
}

async function setupMoney(options: SetupOptions = {}) {
  const config = Object.assign(new BaseEntityAttrDescriptor('price', FormControlType.MONEY, 'Price'), { allowedCurrencies: ['EUR', 'HUF', 'JPY'] }, options.init);
  const entity = new TestEntity('1', 'name');
  if (options.value !== undefined) Reflect.set(entity, 'price', options.value);
  const rates = signal(new ExchangeRates(options.rates ?? []));
  const harness = await setupFormControlTest(MoneyComponent, config, entity, [{ provide: OrganizationExchangeRates, useValue: { rates } }]);
  harness.fixture.detectChanges();
  const component = harness.component as MoneyComponent<TestEntity>;
  const control = component.formGroup.get('price');
  if (!control) throw new Error('the price control was not registered');
  const element = harness.fixture.nativeElement as HTMLElement;
  const amount = () => element.querySelector<HTMLInputElement>('[data-testid="money-amount"]');
  const showIn = () => element.querySelector('[data-testid="money-show-in"]');
  const converted = () => element.querySelector('[data-testid="money-converted"]')?.textContent?.trim();
  return { ...harness, component, control, element, amount, showIn, converted, rates };
}

function type(input: HTMLInputElement | null, text: string): void {
  if (!input) throw new Error('no such input');
  input.value = text;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

describe('MoneyComponent', () => {
  it('shows the stored amount and currency and leaves the control pristine', async () => {
    const { amount, component, control } = await setupMoney({ value: { amount: '19.90', currency: 'HUF' } });

    expect(amount()?.value).toBe('19.90');
    expect(component.fields.controls.currency.value).toBe('HUF');
    expect(control.pristine).toBe(true);
  });

  it('offers the allowed currencies, named in the active language, and starts in the first', async () => {
    const { component } = await setupMoney();

    expect(component.currencyOptions().map((option) => option.code)).toEqual(['EUR', 'HUF', 'JPY']);
    expect(component.currencyOptions()[0].name.toLowerCase()).toContain('euro');
    expect(component.fields.controls.currency.value).toBe('EUR');
  });

  it('starts in the default currency', async () => {
    const { component } = await setupMoney({ init: { defaultCurrency: 'HUF' } });

    expect(component.fields.controls.currency.value).toBe('HUF');
  });

  it('publishes the typed amount with its currency and dirties the control', async () => {
    const { amount, control } = await setupMoney();

    type(amount(), '1234,50');

    expect(control.value).toEqual({ amount: '1234.5', currency: 'EUR' });
    expect(control.dirty).toBe(true);
  });

  it('limits the decimal places to the currency’s minor units', async () => {
    const { amount, control, element, fixture } = await setupMoney();

    type(amount(), '1.005');
    fixture.detectChanges();

    expect(control.valid).toBe(false);
    expect(element.querySelector('[data-testid="money-amount-error"]')?.textContent).toContain('base_entity.measure.error.scale');
    type(amount(), '1.00');
    expect(control.valid).toBe(true);
  });

  it('checks the amount again when the currency changes, and never converts it', async () => {
    const { amount, component, control } = await setupMoney({ value: { amount: '10.5', currency: 'EUR' } });

    component.fields.controls.currency.setValue('JPY');
    component.onCurrencyChanged();
    expect(control.valid).toBe(false);
    expect(amount()?.value).toBe('10.50');

    type(amount(), '11');
    expect(control.value).toEqual({ amount: '11', currency: 'JPY' });
    expect(control.valid).toBe(true);
  });

  it('has no limit for a currency the catalog does not know', async () => {
    const { component } = await setupMoney({ value: { amount: '1.23456', currency: 'ZZZ' } });

    expect(component.scaleLimit()).toBeUndefined();
  });

  it('is invalid while a required amount is empty', async () => {
    const { amount, control } = await setupMoney({ init: { required: true } });

    expect(control.valid).toBe(false);
    type(amount(), '3');
    expect(control.valid).toBe(true);
  });

  it('publishes nothing for an empty or invalid amount', async () => {
    const { amount, control } = await setupMoney({ value: { amount: '1', currency: 'EUR' } });

    type(amount(), '');
    expect(control.value).toBeNull();
    type(amount(), 'abc');
    expect(control.value).toBeNull();
    expect(control.valid).toBe(false);
  });

  it('still shows a stored currency the attribute no longer offers', async () => {
    const { component } = await setupMoney({ value: { amount: '2', currency: 'USD' } });

    expect(component.currencyOptions().map((option) => option.code)).toEqual(['EUR', 'HUF', 'JPY', 'USD']);
  });

  it('disables the fields of a disabled attribute', async () => {
    const { component } = await setupMoney({ value: { amount: '1', currency: 'EUR' }, init: { disabled: true } });

    expect(component.fields.disabled).toBe(true);
    expect(component.isEditable()).toBe(false);
  });
});

describe('MoneyComponent exchange hint', () => {
  const RATES: ExchangeRateData[] = [{ from: 'EUR', to: 'HUF', rate: '400', validFrom: '2020-01-01' }];

  it('offers no hint where the organization has no rates', async () => {
    const { component, showIn } = await setupMoney({ value: { amount: '10.00', currency: 'EUR' } });

    expect(component.hintTargets()).toEqual([]);
    expect(showIn()).toBeNull();
  });

  it('offers the currencies a rate reaches from the one chosen, the inverse direction too', async () => {
    const { component, showIn } = await setupMoney({ value: { amount: '10.00', currency: 'EUR' }, rates: RATES });

    expect(component.hintTargets()).toEqual(['HUF']);
    expect(showIn()).not.toBeNull();
    component.fields.controls.currency.setValue('HUF');
    expect(component.hintTargets()).toEqual(['EUR']);
    component.fields.controls.currency.setValue('JPY');
    expect(component.hintTargets()).toEqual([]);
  });

  it('shows the amount converted at the rate, rounded to the target, and never stores it', async () => {
    const { component, control, fixture, converted } = await setupMoney({ value: { amount: '10.50', currency: 'EUR' }, rates: RATES });

    expect(converted()).toBeUndefined();
    component.hintCurrency.set('HUF');
    fixture.detectChanges();

    expect(component.converted()).toContain('4,200');
    expect(converted()).toContain('≈');
    expect(control.value).toEqual({ amount: '10.50', currency: 'EUR' });
    expect(control.pristine).toBe(true);
  });

  it('follows the amount typed, and shows nothing for an amount that is not one', async () => {
    const { amount, component, fixture } = await setupMoney({ value: { amount: '10.00', currency: 'EUR' }, rates: RATES });
    component.hintCurrency.set('HUF');

    type(amount(), '2');
    fixture.detectChanges();
    expect(component.converted()).toContain('800');
    type(amount(), 'abc');
    fixture.detectChanges();
    expect(component.converted()).toBe('');
  });

  it('shows nothing when the chosen currency changes to one the target is not reachable from', async () => {
    const { component } = await setupMoney({ value: { amount: '10.00', currency: 'EUR' }, rates: RATES });
    component.hintCurrency.set('HUF');
    component.fields.controls.currency.setValue('JPY');

    expect(component.converted()).toBe('');
  });
});
