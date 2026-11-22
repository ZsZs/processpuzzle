import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { QuantityComponent } from './quantity.component';

interface SetupOptions {
  value?: unknown;
  init?: Partial<BaseEntityAttrDescriptor>;
}

async function setupQuantity(options: SetupOptions = {}) {
  const config = Object.assign(new BaseEntityAttrDescriptor('weight', FormControlType.QUANTITY, 'Weight'), { quantityKind: 'MASS' }, options.init);
  const entity = new TestEntity('1', 'name');
  if (options.value !== undefined) Reflect.set(entity, 'weight', options.value);
  const harness = await setupFormControlTest(QuantityComponent, config, entity);
  harness.fixture.detectChanges();
  const component = harness.component as QuantityComponent<TestEntity>;
  const control = component.formGroup.get('weight');
  if (!control) throw new Error('the weight control was not registered');
  const element = harness.fixture.nativeElement as HTMLElement;
  const amount = () => element.querySelector<HTMLInputElement>('[data-testid="quantity-amount"]');
  const toggle = () => element.querySelector<HTMLButtonElement>('[data-testid="measure-convert-toggle"]');
  return { ...harness, component, control, element, amount, toggle };
}

function type(input: HTMLInputElement | null, text: string): void {
  if (!input) throw new Error('no such input');
  input.value = text;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

describe('QuantityComponent', () => {
  it('shows the stored amount and unit and leaves the control pristine', async () => {
    const { amount, component, control } = await setupQuantity({ value: { amount: '12.5', unit: 'g' } });

    expect(amount()?.value).toBe('12.5');
    expect(component.fields.controls.unit.value).toBe('g');
    expect(control.value).toEqual({ amount: '12.5', unit: 'g' });
    expect(control.pristine).toBe(true);
  });

  it('offers the units of the attribute’s kind and starts in the kind’s base unit', async () => {
    const { component } = await setupQuantity();

    expect(component.units().map((unit) => unit.code)).toEqual(['mg', 'g', 'dkg', 'kg', 'q', 't', 'oz', 'lb']);
    expect(component.fields.controls.unit.value).toBe('kg');
  });

  it('starts in the attribute’s default unit', async () => {
    const { component } = await setupQuantity({ init: { defaultUnit: 'g' } });

    expect(component.fields.controls.unit.value).toBe('g');
  });

  it('publishes the typed amount as decimal text — a decimal comma is a point — and dirties the control', async () => {
    const { amount, control } = await setupQuantity();

    type(amount(), '12,50');

    expect(control.value).toEqual({ amount: '12.5', unit: 'kg' });
    expect(control.dirty).toBe(true);
  });

  it('publishes nothing for an empty amount, and blocks an amount that is not a number', async () => {
    const { amount, control } = await setupQuantity({ value: { amount: '1', unit: 'kg' } });

    type(amount(), '');
    expect(control.value).toBeNull();
    expect(control.valid).toBe(true);

    type(amount(), '12.x');
    expect(control.value).toBeNull();
    expect(control.valid).toBe(false);
  });

  it('converts the amount when the unit changes, rounded to six places', async () => {
    const { component, control, amount, fixture } = await setupQuantity({ value: { amount: '1500', unit: 'g' } });

    component.fields.controls.unit.setValue('lb');
    component.onUnitChanged();
    fixture.detectChanges();

    expect(control.value).toEqual({ amount: '3.306934', unit: 'lb' });
    expect(amount()?.value).toBe('3.306934');
  });

  it('rounds a conversion to the attribute’s scale', async () => {
    const { component, control } = await setupQuantity({ value: { amount: '1500', unit: 'g' }, init: { scale: 2 } });

    component.fields.controls.unit.setValue('lb');
    component.onUnitChanged();

    expect(control.value).toEqual({ amount: '3.31', unit: 'lb' });
  });

  it('keeps the number when the user turned conversion off', async () => {
    const { component, control, toggle, fixture } = await setupQuantity({ value: { amount: '1500', unit: 'g' } });

    toggle()?.click();
    fixture.detectChanges();
    expect(component.convertOnUnitChange()).toBe(false);
    expect(toggle()?.getAttribute('aria-pressed')).toBe('false');

    component.fields.controls.unit.setValue('kg');
    component.onUnitChanged();

    expect(control.value).toEqual({ amount: '1500', unit: 'kg' });
  });

  it('keeps the number when the new unit measures something else', async () => {
    const { component, control } = await setupQuantity({ value: { amount: '5', unit: 'kg' }, init: { quantityKind: undefined } });

    component.fields.controls.unit.setValue('m');
    component.onUnitChanged();

    expect(control.value).toEqual({ amount: '5', unit: 'm' });
  });

  it('groups the units by kind when the attribute names none, and offers no conversion switch', async () => {
    const { component, toggle } = await setupQuantity({ init: { quantityKind: undefined } });

    expect(component.groups().length).toBeGreaterThan(5);
    expect(toggle()).toBeNull();
  });

  it('limits the decimal places to the attribute’s scale', async () => {
    const { amount, control, element, fixture } = await setupQuantity({ init: { scale: 1 } });

    type(amount(), '1.25');
    fixture.detectChanges();

    expect(control.valid).toBe(false);
    expect(element.querySelector('[data-testid="quantity-amount-error"]')?.textContent).toContain('base_entity.measure.error.scale');
    type(amount(), '1.2');
    expect(control.valid).toBe(true);
  });

  it('is invalid while a required amount is empty', async () => {
    const { amount, control } = await setupQuantity({ init: { required: true } });

    expect(control.valid).toBe(false);
    type(amount(), '3');
    expect(control.valid).toBe(true);
  });

  it('still shows a stored unit the attribute no longer offers', async () => {
    const { component } = await setupQuantity({ value: { amount: '2', unit: 'm' }, init: { allowedUnits: ['kg', 'g'] } });

    expect(component.units().map((unit) => unit.code)).toEqual(['kg', 'g', 'm']);
    expect(component.fields.controls.unit.value).toBe('m');
  });

  it('shows the symbol in the select’s trigger', async () => {
    const { component } = await setupQuantity();

    expect(component.symbolOf('kg')).toBe('kg');
    expect(component.symbolOf('degC')).toBe('°C');
    expect(component.symbolOf('zork')).toBe('zork');
    expect(component.symbolOf(null)).toBe('');
  });

  it('disables the fields of a disabled attribute and offers no switch', async () => {
    const { component, toggle } = await setupQuantity({ value: { amount: '1', unit: 'kg' }, init: { disabled: true } });

    expect(component.fields.disabled).toBe(true);
    expect(component.isEditable()).toBe(false);
    expect(toggle()).toBeNull();
  });

  it('renders nothing for an invisible attribute', async () => {
    const { element } = await setupQuantity({ init: { visible: false } });

    expect(element.querySelector('fieldset')).toBeNull();
  });
});
