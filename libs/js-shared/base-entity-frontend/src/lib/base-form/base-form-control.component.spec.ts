import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { describe, expect, it, vi } from 'vitest';
import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { TestEntity } from '../test-entity';
import { setupFormControlTest } from '../../test-setup';
import { BaseFormControlComponent } from './base-form-control.component';

@Component({
  selector: 'test-base-form-control',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: '',
})
class TestBaseFormControl extends BaseFormControlComponent<TestEntity> {}

describe('BaseFormControlComponent', () => {
  const testEntity = new TestEntity('1', 'Test');

  it('linkedEntityName() throws and logs when linkedEntityType is not configured', async () => {
    const config = new BaseEntityAttrDescriptor('number', FormControlType.FOREIGN_KEY);
    const { component } = await setupFormControlTest(TestBaseFormControl, config, testEntity);
    if (!component) throw new Error('Expected component to be defined');
    const errorSpy = vi.spyOn(component['logger'], 'error').mockImplementation(() => undefined);

    expect(() => component.linkedEntityName()).toThrow(/linkedEntityType should be defined for 'number'/);
    expect(errorSpy).toHaveBeenCalled();
  });

  it('serialises the style descriptor into the host style attribute', async () => {
    const config = new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX);
    config.style = { color: 'red', 'font-weight': 'bold' };
    const { fixture } = await setupFormControlTest(TestBaseFormControl, config, testEntity);
    fixture.detectChanges();

    const host = fixture.nativeElement.querySelector('test-base-form-control') as HTMLElement | null;
    expect(host?.getAttribute('style')).toContain('color:red');
    expect(host?.getAttribute('style')).toContain('font-weight:bold');
  });

  it('exposes no host style attribute when the descriptor omits style', async () => {
    const config = new BaseEntityAttrDescriptor('number', FormControlType.TEXT_BOX);
    const { fixture } = await setupFormControlTest(TestBaseFormControl, config, testEntity);
    fixture.detectChanges();

    const host = fixture.nativeElement.querySelector('test-base-form-control') as HTMLElement | null;
    expect(host?.getAttribute('style')).toBeNull();
  });
});
