import { TestEntity } from '../../test-entity';
import { BaseEntityAttrDescriptor, FormControlType } from '../../base-entity/base-entity-attr.descriptor';
import { setupFormControlTest } from '../../../test-setup';
import { CheckboxComponent } from '../checkbox/checkbox.component';

describe('TextboxComponent', () => {
  const textboxConfig = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Project Name');
  textboxConfig.placeholder = 'Enter project name';
  const testEntity: TestEntity = new TestEntity('1', 'Dynamic component angular app');

  it('should create', async () => {
    const { containerComponent, component } = await setupFormControlTest(CheckboxComponent, textboxConfig, testEntity);
    expect(containerComponent).toBeTruthy();
    expect(component).toBeTruthy();
  });
});
