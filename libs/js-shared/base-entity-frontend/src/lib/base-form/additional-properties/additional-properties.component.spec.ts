import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { AdditionalPropertiesComponent } from './additional-properties.component';

function makeConfig(): BaseEntityAttrDescriptor {
  return new BaseEntityAttrDescriptor('additionalProperties', FormControlType.ADDITIONAL_PROPERTIES, 'Additional Properties');
}

function makeEntity(value?: unknown): TestEntity {
  const entity = new TestEntity('1', 'name');
  if (value !== undefined) Reflect.set(entity, 'additionalProperties', value);
  return entity;
}

async function setupControl(config: BaseEntityAttrDescriptor, value?: unknown) {
  const entity = makeEntity(value);
  const harness = await setupFormControlTest(AdditionalPropertiesComponent, config, entity, []);
  return { ...harness, entity, component: harness.component as AdditionalPropertiesComponent<TestEntity> };
}

function makeInput(value = ''): HTMLInputElement {
  const input = document.createElement('input');
  input.value = value;
  return input;
}

describe('AdditionalPropertiesComponent', () => {
  describe('entries()', () => {
    it('projects the record into an array of key/value entries', async () => {
      const { component } = await setupControl(makeConfig(), { alpha: 'A', beta: 'B' });

      expect(component.entries()).toEqual([
        { key: 'alpha', value: 'A' },
        { key: 'beta', value: 'B' },
      ]);
    });

    it('returns an empty array when the value is null', async () => {
      const { component } = await setupControl(makeConfig(), null);

      expect(component.entries()).toEqual([]);
    });

    it('returns an empty array when the value is a string', async () => {
      const { component } = await setupControl(makeConfig(), 'not-a-record');

      expect(component.entries()).toEqual([]);
    });

    it('returns an empty array when the value is an array', async () => {
      const { component } = await setupControl(makeConfig(), ['not', 'a', 'record']);

      expect(component.entries()).toEqual([]);
    });
  });

  describe('editing state', () => {
    it('starts in idle mode', async () => {
      const { component } = await setupControl(makeConfig(), {});

      expect(component.editing()).toBe(false);
    });

    it('enterEditMode() flips editing to true', async () => {
      const { component } = await setupControl(makeConfig(), {});

      component.enterEditMode();

      expect(component.editing()).toBe(true);
    });

    it('cancelEdit() flips editing back to false', async () => {
      const { component } = await setupControl(makeConfig(), {});
      component.enterEditMode();

      component.cancelEdit();

      expect(component.editing()).toBe(false);
    });
  });

  describe('add()', () => {
    it('appends a new key/value pair, marks the control dirty and touched, and clears the inputs', async () => {
      const { component } = await setupControl(makeConfig(), { alpha: 'A' });
      const keyInput = makeInput('  beta  ');
      const valueInput = makeInput('  B  ');

      component.add(keyInput, valueInput);

      expect(component.entries()).toEqual([
        { key: 'alpha', value: 'A' },
        { key: 'beta', value: 'B' },
      ]);
      expect(keyInput.value).toBe('');
      expect(valueInput.value).toBe('');
      expect(component.formGroup.get('additionalProperties')?.dirty).toBe(true);
      expect(component.formGroup.get('additionalProperties')?.touched).toBe(true);
    });

    it('overwrites the value when the key already exists', async () => {
      const { component } = await setupControl(makeConfig(), { alpha: 'A' });

      component.add(makeInput('alpha'), makeInput('changed'));

      expect(component.entries()).toEqual([{ key: 'alpha', value: 'changed' }]);
    });

    it('does nothing when the key is empty', async () => {
      const { component } = await setupControl(makeConfig(), { alpha: 'A' });

      component.add(makeInput('   '), makeInput('anything'));

      expect(component.entries()).toEqual([{ key: 'alpha', value: 'A' }]);
      expect(component.formGroup.get('additionalProperties')?.dirty).toBe(false);
    });

    it('accepts an empty value', async () => {
      const { component } = await setupControl(makeConfig(), {});

      component.add(makeInput('flag'), makeInput(''));

      expect(component.entries()).toEqual([{ key: 'flag', value: '' }]);
    });
  });

  describe('remove()', () => {
    it('removes only the given key and marks the control dirty', async () => {
      const { component } = await setupControl(makeConfig(), { alpha: 'A', beta: 'B', gamma: 'C' });

      component.remove('beta');

      expect(component.entries()).toEqual([
        { key: 'alpha', value: 'A' },
        { key: 'gamma', value: 'C' },
      ]);
      expect(component.formGroup.get('additionalProperties')?.dirty).toBe(true);
    });

    it('is a no-op when the key does not exist (still writes back the same map)', async () => {
      const { component } = await setupControl(makeConfig(), { alpha: 'A' });

      component.remove('unknown');

      expect(component.entries()).toEqual([{ key: 'alpha', value: 'A' }]);
    });
  });

  describe('template', () => {
    it('renders one row per entry with a remove button when visible', async () => {
      const { fixture } = await setupControl(makeConfig(), { alpha: 'A', beta: 'B' });
      const host = fixture.nativeElement as HTMLElement;

      const rows = host.querySelectorAll('.base-entity-form-list li');
      expect(rows).toHaveLength(2);
      expect(host.querySelector('button[aria-label="remove alpha"]')).not.toBeNull();
      expect(host.querySelector('button[aria-label="remove beta"]')).not.toBeNull();
    });

    it('does not render the edit row until enterEditMode() is called', async () => {
      const { component, fixture } = await setupControl(makeConfig(), {});
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('.edit-row')).toBeNull();

      component.enterEditMode();
      fixture.detectChanges();

      expect(host.querySelector('.edit-row')).not.toBeNull();
    });

    it('renders nothing when the descriptor is not visible', async () => {
      const config = makeConfig();
      config.visible = false;
      const { fixture } = await setupControl(config, { alpha: 'A' });
      const host = fixture.nativeElement as HTMLElement;

      expect(host.querySelector('.base-entity-form-list')).toBeNull();
      expect(host.querySelector('.edit-row')).toBeNull();
    });
  });
});
