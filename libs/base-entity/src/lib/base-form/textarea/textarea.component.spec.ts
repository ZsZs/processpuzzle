import { TestEntity } from '../../test-entity';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { setupFormControlTest } from '../../../test-setup';
import { CheckboxComponent } from '../checkbox/checkbox.component';

describe('TextareaComponent', () => {
  const textareaConfig = new BaseEntityAttrDescriptor('projectFeedback', FormControlType.TEXTAREA, 'Project feedback', undefined, false, { inputType: 'text' });
  textareaConfig.disabled = false;
  textareaConfig.placeholder = 'Enter project feedback';
  textareaConfig.styleClass = 'col-md-6 mt-3';
  textareaConfig.lines = 5;
  textareaConfig.visible = true;
  const testEntity: TestEntity = new TestEntity('1', 'testEntity', 'This project is developed in Angular v14');

  it('should create', async () => {
    const { containerComponent, component } = await setupFormControlTest(CheckboxComponent, textareaConfig, testEntity);
    expect(containerComponent).toBeTruthy();
    expect(component).toBeTruthy();
  });
});
