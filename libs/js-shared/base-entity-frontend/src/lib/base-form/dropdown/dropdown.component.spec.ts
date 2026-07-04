import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatSelect } from '@angular/material/select';
import { TestEntity } from '../../test-entity';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { setupFormControlTest } from '../../../test-setup';
import { CheckboxComponent } from '../checkbox/checkbox.component';
import { DropdownComponent } from './dropdown.component';
import { describe, expect, it } from 'vitest';

describe('DropdownComponent', () => {
  const dropdownConfig = new BaseEntityAttrDescriptor('selectable', FormControlType.DROPDOWN, 'Project Status');
  dropdownConfig.selectables = [
    { key: '1', value: 'Not Started' },
    { key: '2', value: 'Kick off' },
    { key: '3', value: 'In Progress' },
    { key: '4', value: 'On Hold' },
    { key: '5', value: 'Completed' },
  ];
  const testEntity: TestEntity = new TestEntity('1', 'dropdown', undefined, true);
  testEntity.selectable = 4;

  it('should create', async () => {
    const { containerComponent, component } = await setupFormControlTest(CheckboxComponent, dropdownConfig, testEntity);
    expect(containerComponent).toBeTruthy();
    expect(component).toBeTruthy();
  });

  it('renders stably across CD cycles when selectables is a factory returning a fresh array (regression: NG0956/NG0103)', async () => {
    const factoryConfig = new BaseEntityAttrDescriptor('selectable', FormControlType.DROPDOWN, 'Project Status');
    factoryConfig.selectables = () =>
      ['Not Started', 'Kick off', 'In Progress', 'On Hold', 'Completed'].map((value, index) => ({ key: String(index + 1), value }));
    const entity = new TestEntity('1', 'dropdown', undefined, true);
    entity.selectable = 4;

    const { fixture } = await setupFormControlTest(DropdownComponent, factoryConfig, entity, [provideNoopAnimations()]);
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();

    const matSelect = fixture.debugElement.query(By.directive(MatSelect)).componentInstance as MatSelect;
    expect(matSelect.options.length).toBe(5);
    expect(matSelect.options.map((o) => String(o.value))).toEqual(['Not Started', 'Kick off', 'In Progress', 'On Hold', 'Completed']);
  });
});
