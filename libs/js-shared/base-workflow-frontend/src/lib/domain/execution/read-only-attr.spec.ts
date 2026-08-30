import { describe, expect, it } from 'vitest';
import { FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { readOnlyAttr } from './read-only-attr';

describe('readOnlyAttr', () => {
  // `disabled` is passed straight to `new FormControl({ value, disabled })` by the form builder, so the
  // control is greyed out — and, because a disabled control never becomes dirty, Save stays disabled too.
  it('disables the attribute, which is what greys the control and keeps the form clean', () => {
    expect(readOnlyAttr('status', FormControlType.TEXT_BOX, 'Status').disabled).toBe(true);
  });

  it('passes the label, the control type and the selectables through unchanged', () => {
    const selectables = toSelectables(['ACTIVE', 'COMPLETED']);

    const attrDescriptor = readOnlyAttr('status', FormControlType.DROPDOWN, 'Status', selectables);

    expect(attrDescriptor.attrName).toBe('status');
    expect(attrDescriptor.formControlType).toBe(FormControlType.DROPDOWN);
    expect(attrDescriptor.label).toBe('Status');
    expect(attrDescriptor.getSelectables()).toEqual(selectables);
  });

  it('forwards isLinkToDetails, so a read-only row can still open its own form', () => {
    expect(readOnlyAttr('name', FormControlType.TEXT_BOX, 'Name', undefined, true).isLinkToDetails).toBe(true);
    expect(readOnlyAttr('name', FormControlType.TEXT_BOX, 'Name').isLinkToDetails).toBeUndefined();
  });

  // Read-only is not "invisible": the value still has to render.
  it('leaves the attribute visible', () => {
    expect(readOnlyAttr('status', FormControlType.TEXT_BOX, 'Status').visible).toBe(true);
  });
});
