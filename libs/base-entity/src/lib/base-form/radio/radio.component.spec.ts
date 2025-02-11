import { TestEntity } from '../../test-entity';
import { BaseEntityAttrDescriptor, FormControlType } from '../../base-entity/base-entity-attr.descriptor';
import { setupFormControlTest } from '../../../test-setup';
import { CheckboxComponent } from '../checkbox/checkbox.component';

describe('RadioComponent', () => {
  const radioConfig = new BaseEntityAttrDescriptor('projectCloudTechnology', FormControlType.RADIO, 'Project cloud technology');
  radioConfig.selectables = [
    { key: '1', value: 'AWS' },
    { key: '2', value: 'MS Azure' },
    { key: '3', value: 'GCP' },
  ];

  const testEntity: TestEntity = new TestEntity('1', 'radio', undefined, true);
  testEntity.selectable = 3;

  it('should display radio component if visible true', async () => {
    const { containerComponent, component } = await setupFormControlTest(CheckboxComponent, radioConfig, testEntity);
    expect(containerComponent).toBeTruthy();
    expect(component).toBeTruthy();
  });
});
