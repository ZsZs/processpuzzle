import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { formatDateValue } from '../../base-entity/date-format';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { provideLocaleDateAdapter } from './locale-date.adapter';
import { DatepickerComponent } from './datepicker.component';

async function setup(dateFormat?: BaseEntityAttrDescriptor['dateFormat'], date: unknown = new Date(2026, 0, 15, 9, 30)) {
  const config = new BaseEntityAttrDescriptor('date', FormControlType.DATE, 'Date');
  config.dateFormat = dateFormat;
  const entity = new TestEntity('1', 'name');
  // A metadata-defined entity holds the payload's string; a hand-written one may hold a Date.
  entity.date = date as Date;
  const result = await setupFormControlTest(DatepickerComponent, config, entity, [provideLocaleDateAdapter()]);
  const element = result.fixture.nativeElement as HTMLElement;
  return { ...result, element, component: result.component as DatepickerComponent<TestEntity> };
}

describe('DatepickerComponent', () => {
  it('reports a missing form control explicitly', async () => {
    const { component } = await setup();
    component.formGroup.removeControl('date');

    expect(() => component.ngOnInit()).toThrow("Form control 'date' is missing.");
  });

  it('hints with today in the attribute style instead of a fixed pattern', async () => {
    const { element } = await setup({ dateStyle: 'short' });

    const hint = element.querySelector('mat-hint')?.textContent?.trim();
    expect(hint).toBe(formatDateValue(new Date(), { dateStyle: 'short' }, 'en'));
    expect(hint).not.toBe('MM-DD-YYYY');
  });

  it('shows the value in the attribute style', async () => {
    const { element } = await setup({ dateStyle: 'short' });

    expect(element.querySelector<HTMLInputElement>('input.pp-date-input')?.value).toBe(formatDateValue(new Date(2026, 0, 15), { dateStyle: 'short' }, 'en'));
  });

  it('has no time field unless the format has a timeStyle', async () => {
    const { element } = await setup({ dateStyle: 'short' });

    expect(element.querySelector('input.pp-date-input')).not.toBeNull();
    expect(element.querySelector('input.pp-time-input')).toBeNull();
  });

  it('adds a time field for a timeStyle', async () => {
    const { element } = await setup({ dateStyle: 'short', timeStyle: 'short' });

    expect(element.querySelector('input.pp-time-input')).not.toBeNull();
    expect(element.querySelector('mat-timepicker-toggle')).not.toBeNull();
  });

  it('saves a date-only pick as the local day when the entity holds a string', async () => {
    const { component } = await setup({ dateStyle: 'short' }, '2026-01-15');

    component.onDateChange(new Date(2026, 1, 20));

    expect(component.control.value).toBe('2026-02-20');
    expect(component.control.dirty).toBe(true);
  });

  it('keeps the time of day when a new date is picked for a date-time format', async () => {
    const { component } = await setup({ dateStyle: 'short', timeStyle: 'short' });

    component.onDateChange(new Date(2026, 1, 20));

    expect(component.control.value).toEqual(new Date(2026, 1, 20, 9, 30));
  });

  it('flags an unparseable date instead of saving it', async () => {
    const { component } = await setup({ dateStyle: 'short' });

    component.onDateChange(new Date(Number.NaN));

    expect(component.control.hasError('matDatepickerParse')).toBe(true);
    expect(component.control.value).toEqual(new Date(2026, 0, 15, 9, 30));
  });
});
