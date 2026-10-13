import { describe, expect, it } from 'vitest';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../../test-entity';
import { setupFormControlTest } from '../../../test-setup';
import { EmbeddedListComponent } from './embedded-list.component';
import { FormArray, FormGroup } from '@angular/forms';

function makeConfig(childDescriptors: BaseEntityAttrDescriptor[] = []): BaseEntityAttrDescriptor {
  const descriptor = new BaseEntityAttrDescriptor('items', FormControlType.EMBEDDED_LIST, 'Items');
  descriptor.childDescriptors = childDescriptors;
  return descriptor;
}

function makeEntity(items?: unknown): TestEntity {
  const entity = new TestEntity('1', 'name');
  if (items !== undefined) Reflect.set(entity, 'items', items);
  return entity;
}

async function setupEmbeddedList(config: BaseEntityAttrDescriptor, itemsValue?: unknown) {
  const entity = makeEntity(itemsValue);
  const harness = await setupFormControlTest(EmbeddedListComponent, config, entity, []);
  return { ...harness, entity, component: harness.component as EmbeddedListComponent<TestEntity> };
}

describe('EmbeddedListComponent', () => {
  it('creates FormArray with a row FormGroup for each item in initial value', async () => {
    const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title');
    const config = makeConfig([titleAttr]);
    config.rowLabelAttr = 'title';

    const { component } = await setupEmbeddedList(config, [{ title: 'First' }, { title: 'Second' }]);

    const formArray = component.formGroup.get('items') as FormArray<FormGroup>;
    expect(formArray).toBeInstanceOf(FormArray);
    expect(formArray).toHaveLength(2);
    expect(component.rows()).toHaveLength(2);
    expect(formArray.at(0).get('title')?.value).toBe('First');
    expect(formArray.at(1).get('title')?.value).toBe('Second');
  });

  it('labels the row by rowLabelAttr when present', async () => {
    const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title');
    const config = makeConfig([titleAttr]);
    config.rowLabelAttr = 'title';

    const { component } = await setupEmbeddedList(config, [{ title: 'My Card' }]);

    expect(component.rowLabel(component.rows()[0], 0)).toBe('My Card');
  });

  it('falls back to index label when rowLabelAttr is missing or empty', async () => {
    const config = makeConfig([]);

    const { component } = await setupEmbeddedList(config, [{}]);

    expect(component.rowLabel(component.rows()[0], 0)).toBe('Items #1');
  });

  it('adds a new row when addRow is called', async () => {
    const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title');
    const config = makeConfig([titleAttr]);

    const { component } = await setupEmbeddedList(config, [{ title: 'Initial' }]);

    component.addRow();

    const formArray = component.formGroup.get('items') as FormArray<FormGroup>;
    expect(formArray).toHaveLength(2);
    expect(component.rows()).toHaveLength(2);
    expect(formArray.dirty).toBe(true);
  });

  it('deletes a row by index when deleteRow is called', async () => {
    const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title');
    const config = makeConfig([titleAttr]);

    const { component } = await setupEmbeddedList(config, [{ title: 'A' }, { title: 'B' }, { title: 'C' }]);

    component.deleteRow(1);

    const formArray = component.formGroup.get('items') as FormArray<FormGroup>;
    expect(formArray).toHaveLength(2);
    expect(component.rows()).toHaveLength(2);
    expect(formArray.at(0).get('title')?.value).toBe('A');
    expect(formArray.at(1).get('title')?.value).toBe('C');
    expect(formArray.dirty).toBe(true);
  });

  it('reorders rows when moveRow is called', async () => {
    const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title');
    const config = makeConfig([titleAttr]);

    const { component } = await setupEmbeddedList(config, [{ title: 'First' }, { title: 'Second' }]);

    component.moveRow(0, 1);

    const formArray = component.formGroup.get('items') as FormArray<FormGroup>;
    expect(formArray.at(0).get('title')?.value).toBe('Second');
    expect(formArray.at(1).get('title')?.value).toBe('First');
    expect(formArray.dirty).toBe(true);
  });

  it('renders expansion panels and Add button in template', async () => {
    const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title');
    const config = makeConfig([titleAttr]);
    config.rowLabelAttr = 'title';

    const { fixture } = await setupEmbeddedList(config, [{ title: 'Card 1' }]);
    const host = fixture.nativeElement as HTMLElement;

    expect(host.querySelector('mat-accordion')).not.toBeNull();
    expect(host.querySelector('mat-expansion-panel')).not.toBeNull();
    expect(host.querySelector('button[data-testid="embedded-list-add"]')).not.toBeNull();
  });
});
