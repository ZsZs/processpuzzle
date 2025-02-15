import { TestEntity } from '../test-entity';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { setupFormComponentTest } from '../../test-setup';

describe('BaseFormHostDirective', () => {
  const checkboxConfig: BaseEntityAttrDescriptor = new BaseEntityAttrDescriptor('projectApproved', FormControlType.CHECKBOX, 'Is project approved from the business?', undefined, false, {
    inputType: 'text',
  });
  const testEntity: TestEntity = new TestEntity('1', undefined, undefined, true);

  it('should create an instance', async () => {
    const { component } = await setupFormComponentTest([checkboxConfig], testEntity);
    expect(component.componentHost).toBeTruthy();
  });
});
