import { TestEntity } from '../../test-entity';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { setupFormControlTest } from '../../../test-setup';
import { CheckboxComponent } from '../checkbox/checkbox.component';

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
});
