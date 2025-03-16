import { CheckboxComponent } from './checkbox.component';
import { TestEntity } from '../../test-entity';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { setupFormControlTest } from '../../../test-setup';

describe('CheckboxComponent', () => {
  const checkboxConfig: BaseEntityAttrDescriptor = new BaseEntityAttrDescriptor('boolean', FormControlType.CHECKBOX, 'Is project approved from the business?');
  checkboxConfig.styleClass = 'mt-3';
  checkboxConfig.labelClass = 'ms-2';
  checkboxConfig.visible = true;
  checkboxConfig.disabled = true;
  const testEntity: TestEntity = new TestEntity('1', undefined, undefined, true);

  it('should display checkbox component if visible true', async () => {
    const { containerComponent, component } = await setupFormControlTest(CheckboxComponent, checkboxConfig, testEntity);

    expect(containerComponent).toBeTruthy();
    expect(component).toBeTruthy();
  });
});
