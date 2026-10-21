import { FormControl, FormGroup } from '@angular/forms';
import { render, screen, waitFor } from '@testing-library/angular';
import { BaseEntityFormBuilder, DynamicEntityRegistry } from '@processpuzzle/base-entity';
import { describe, expect, it, vi } from 'vitest';
import { EntityFormComponent } from './entity-form.component';

const store = {};
const descriptor = { attrDescriptors: [{ attrName: 'orderNumber' }], store };
const order = { id: 'o-1', orderNumber: 'ORD-1001' };

async function renderForm(inputs: { entityName?: string; entity?: Record<string, unknown>; readOnly?: boolean }, resolved: unknown = { entityName: 'Order', descriptor }) {
  const buildForm = vi.fn((_host: unknown, form: FormGroup, ..._rest: unknown[]) => form.addControl('orderNumber', new FormControl('ORD-1001')));
  const resolve = vi.fn().mockResolvedValue(resolved);
  const result = await render(EntityFormComponent, {
    inputs,
    providers: [
      { provide: DynamicEntityRegistry, useValue: { resolve } },
      { provide: BaseEntityFormBuilder, useValue: { buildForm } },
    ],
  });
  return { ...result, resolve, buildForm };
}

describe('EntityFormComponent', () => {
  it('builds the form of the named entity from the object it is given, disabled by default', async () => {
    const { resolve, buildForm, fixture } = await renderForm({ entityName: 'Order', entity: order });

    await waitFor(() => expect(buildForm).toHaveBeenCalledOnce());
    expect(resolve).toHaveBeenCalledWith('Order');
    expect(buildForm.mock.calls[0][2]).toBe(store);
    expect(buildForm.mock.calls[0][3]).toBe(descriptor.attrDescriptors);
    expect((fixture.componentInstance as unknown as { form: FormGroup }).form.disabled).toBe(true);
  });

  it('leaves the controls enabled when readOnly is switched off', async () => {
    const { buildForm, fixture } = await renderForm({ entityName: 'Order', entity: order, readOnly: false });

    await waitFor(() => expect(buildForm).toHaveBeenCalledOnce());
    expect((fixture.componentInstance as unknown as { form: FormGroup }).form.enabled).toBe(true);
  });

  it('builds nothing until an object arrives', async () => {
    const { resolve, buildForm } = await renderForm({ entityName: 'Order' });

    await waitFor(() => expect(resolve).toHaveBeenCalled());
    expect(buildForm).not.toHaveBeenCalled();
  });

  it('says so when the entity type is not registered', async () => {
    await renderForm({ entityName: 'Nope', entity: order }, null);

    await waitFor(() => expect(screen.getByTestId('entity-form-unresolved').textContent).toContain('Nope'));
  });
});
