import { FlexBoxComponent } from './flex-box.component';
import { TestEntity } from '../../test-entity';
import { FlexboxDescriptor, FlexDirection } from '../../base-entity/flexboxDescriptor';
import { setupFormControlTest } from '../../../test-setup';

describe('FlexControlComponent', () => {
  const flexBoxConfig = new FlexboxDescriptor([], FlexDirection.CONTAINER);
  const testEntity: TestEntity = new TestEntity('1', 'dropdown', undefined, true);

  it('should create', async () => {
    const { containerComponent, component } = await setupFormControlTest(FlexBoxComponent, flexBoxConfig, testEntity);
    expect(containerComponent).toBeTruthy();
    expect(component).toBeTruthy();
    expect((component as FlexBoxComponent<TestEntity>).flexBoxHost).toBeTruthy();
  });
});
