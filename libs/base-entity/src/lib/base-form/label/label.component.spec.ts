import { TestEntity } from '../../test-entity';
import { FormControlType } from '../../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../../base-entity/base-entity-attr.descriptor';
import { setupFormControlTest } from '../../../test-setup';
import { By } from '@angular/platform-browser';
import { LabelComponent } from './label.component';

describe('LabelComponent', () => {
  const labelConfig = new BaseEntityAttrDescriptor('description', FormControlType.LABEL);
  labelConfig.visible = true;
  labelConfig.isHeading = false;
  const testEntity: TestEntity = new TestEntity('1', 'label', 'Dynamic component loader');

  it('should display label component if visible true', async () => {
    const { containerComponent, component } = await setupFormControlTest(LabelComponent, labelConfig, testEntity);
    expect(containerComponent).toBeTruthy();
    expect(component).toBeTruthy();
  });

  it('should display "Dynamic component loader"', async () => {
    const { fixture } = await setupFormControlTest(LabelComponent, labelConfig, testEntity);
    expect(labelConfig.isHeading).toBeFalsy();
    const labelElement = fixture.debugElement.query(By.css(`#${labelConfig.attrName}`)).nativeElement;
    expect(labelElement.innerHTML).toContain('Dynamic component loader');
  });
});
