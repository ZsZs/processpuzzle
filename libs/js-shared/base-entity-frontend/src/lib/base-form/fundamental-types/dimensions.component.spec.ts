import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { DimensionsComponent } from './dimensions.component';

interface SetupOptions {
  value?: unknown;
  init?: Partial<BaseEntityAttrDescriptor>;
}

async function setupDimensions(options: SetupOptions = {}) {
  const config = Object.assign(new BaseEntityAttrDescriptor('size', FormControlType.DIMENSIONS, 'Size'), options.init);
  const entity = new TestEntity('1', 'name');
  if (options.value !== undefined) Reflect.set(entity, 'size', options.value);
  const harness = await setupFormControlTest(DimensionsComponent, config, entity);
  harness.fixture.detectChanges();
  const component = harness.component as DimensionsComponent<TestEntity>;
  const control = component.formGroup.get('size');
  if (!control) throw new Error('the size control was not registered');
  const element = harness.fixture.nativeElement as HTMLElement;
  const input = (axis: string) => element.querySelector<HTMLInputElement>(`[data-testid="dimensions-${axis}"]`);
  return { ...harness, component, control, element, input };
}

function type(input: HTMLInputElement | null, text: string): void {
  if (!input) throw new Error('no such input');
  input.value = text;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

describe('DimensionsComponent', () => {
  it('has two axes by default and starts in metres', async () => {
    const { component, input } = await setupDimensions();

    expect(component.axes()).toEqual(['width', 'height']);
    expect(input('width')).not.toBeNull();
    expect(input('depth')).toBeNull();
    expect(component.fields.controls.unit.value).toBe('m');
    expect(component.units().every((unit) => unit.kind === 'LENGTH')).toBe(true);
  });

  it('has a depth when the attribute asks for three dimensions', async () => {
    const { input } = await setupDimensions({ init: { dimensionCount: 3 } });

    expect(input('depth')).not.toBeNull();
  });

  it('shows the stored value and leaves the control pristine', async () => {
    const { input, control } = await setupDimensions({ value: { width: '120', height: '80', depth: '50', unit: 'cm' }, init: { dimensionCount: 3 } });

    expect([input('width')?.value, input('height')?.value, input('depth')?.value]).toEqual(['120', '80', '50']);
    expect(control.pristine).toBe(true);
  });

  it('publishes the axes with the unit once every axis is filled', async () => {
    const { input, control } = await setupDimensions({ init: { dimensionCount: 3 } });

    type(input('width'), '1,2');
    type(input('height'), '0.8');
    expect(control.value).toBeNull();
    type(input('depth'), '0.5');

    expect(control.value).toEqual({ width: '1.2', height: '0.8', depth: '0.5', unit: 'm' });
    expect(control.dirty).toBe(true);
  });

  it('publishes a 2D value without a depth', async () => {
    const { input, control } = await setupDimensions();

    type(input('width'), '3');
    type(input('height'), '4');

    expect(control.value).toEqual({ width: '3', height: '4', unit: 'm' });
  });

  it('blocks a half-filled value, and says so once the user has touched it', async () => {
    const { input, control, element, fixture } = await setupDimensions();

    type(input('width'), '3');
    fixture.detectChanges();

    expect(control.valid).toBe(false);
    expect(element.querySelector('[data-testid="dimensions-incomplete"]')).not.toBeNull();
    type(input('width'), '');
    expect(control.valid).toBe(true);
    expect(control.value).toBeNull();
  });

  it('blocks an axis that is not a number', async () => {
    const { input, control } = await setupDimensions();

    type(input('width'), '3');
    type(input('height'), 'x');

    expect(control.valid).toBe(false);
    expect(control.value).toBeNull();
  });

  it('converts every axis when the unit changes', async () => {
    const { component, control, input, fixture } = await setupDimensions({ value: { width: '120', height: '80', depth: '50', unit: 'cm' }, init: { dimensionCount: 3 } });

    component.fields.controls.unit.setValue('m');
    component.onUnitChanged();
    fixture.detectChanges();

    expect(control.value).toEqual({ width: '1.2', height: '0.8', depth: '0.5', unit: 'm' });
    expect(input('width')?.value).toBe('1.2');
  });

  it('keeps the numbers when the user turned conversion off', async () => {
    const { component, control, element, fixture } = await setupDimensions({ value: { width: '120', height: '80', unit: 'cm' } });

    element.querySelector<HTMLButtonElement>('[data-testid="measure-convert-toggle"]')?.click();
    fixture.detectChanges();
    component.fields.controls.unit.setValue('m');
    component.onUnitChanged();

    expect(control.value).toEqual({ width: '120', height: '80', unit: 'm' });
  });

  it('converts only the axes that have an amount', async () => {
    const { component, input } = await setupDimensions({ init: { allowedUnits: ['cm', 'm'], defaultUnit: 'cm' } });

    type(input('width'), '250');
    component.fields.controls.unit.setValue('m');
    component.onUnitChanged();

    expect(component.fields.controls.width.value).toBe('2.5');
    expect(component.fields.controls.height.value).toBeNull();
  });

  it('is invalid while a required attribute has no value', async () => {
    const { input, control } = await setupDimensions({ init: { required: true } });

    expect(control.valid).toBe(false);
    type(input('width'), '1');
    type(input('height'), '2');
    expect(control.valid).toBe(true);
  });

  it('limits the decimal places to the attribute’s scale', async () => {
    const { input, control } = await setupDimensions({ init: { scale: 1 } });

    type(input('width'), '1.25');
    type(input('height'), '2');

    expect(control.valid).toBe(false);
  });

  it('offers only the allowed lengths, and keeps a stored unit it no longer offers', async () => {
    const { component } = await setupDimensions({ value: { width: '1', height: '2', unit: 'km' }, init: { allowedUnits: ['cm', 'mm'] } });

    expect(component.units().map((unit) => unit.code)).toEqual(['cm', 'mm', 'km']);
    expect(component.symbolOf('km')).toBe('km');
    expect(component.symbolOf(null)).toBe('');
  });

  it('disables the fields of a disabled attribute', async () => {
    const { component } = await setupDimensions({ value: { width: '1', height: '2', unit: 'm' }, init: { disabled: true } });

    expect(component.fields.disabled).toBe(true);
  });
});
