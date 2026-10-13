import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { EmbeddedObjectComponent } from './embedded-object.component';
import { FormGroup } from '@angular/forms';

function makeConfig(childDescriptors: BaseEntityAttrDescriptor[] = []): BaseEntityAttrDescriptor {
  const descriptor = new BaseEntityAttrDescriptor('details', FormControlType.EMBEDDED_OBJECT, 'Details');
  descriptor.childDescriptors = childDescriptors;
  return descriptor;
}

function makeEntity(details?: unknown): TestEntity {
  const entity = new TestEntity('1', 'name');
  if (details !== undefined) Reflect.set(entity, 'details', details);
  return entity;
}

async function setupEmbeddedObject(config: BaseEntityAttrDescriptor, detailsValue?: unknown) {
  const entity = makeEntity(detailsValue);
  const harness = await setupFormControlTest(EmbeddedObjectComponent, config, entity, []);
  return { ...harness, entity, component: harness.component as EmbeddedObjectComponent<TestEntity> };
}

describe('EmbeddedObjectComponent', () => {
  it('builds a nested FormGroup with child controls for its child descriptors', async () => {
    const child1 = new BaseEntityAttrDescriptor('fieldA', FormControlType.TEXT_BOX, 'Field A');
    const child2 = new BaseEntityAttrDescriptor('fieldB', FormControlType.TEXT_BOX, 'Field B');
    const config = makeConfig([child1, child2]);

    const { component } = await setupEmbeddedObject(config, { fieldA: 'valA', fieldB: 'valB' });

    const group = component.formGroup.get('details') as FormGroup;
    expect(group).toBeInstanceOf(FormGroup);
    expect(group.get('fieldA')?.value).toBe('valA');
    expect(group.get('fieldB')?.value).toBe('valB');
  });

  it('renders a fieldset with legend', async () => {
    const config = makeConfig([]);
    const { fixture } = await setupEmbeddedObject(config, {});
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('fieldset.base-entity-form-field')).not.toBeNull();
    expect(host.querySelector('legend')?.textContent?.trim()).toBe('Details');
  });

  it('disables child controls when the parent descriptor is disabled', async () => {
    const child = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title');
    const config = makeConfig([child]);
    config.disabled = true;

    const { component } = await setupEmbeddedObject(config, { title: 'disabled title' });

    const group = component.formGroup.get('details') as FormGroup;
    expect(group.disabled).toBe(true);
  });
});
