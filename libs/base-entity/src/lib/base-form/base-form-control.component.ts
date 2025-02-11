import { Component, input, Signal } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';

@Component({
  standalone: true,
  template: ``,
  imports: [ReactiveFormsModule],
})
export abstract class BaseFormControlComponent<Entity extends BaseEntity> {
  config: Signal<BaseEntityAttrDescriptor> = input.required<BaseEntityAttrDescriptor>();
  entity: Signal<Entity> = input.required();
  formGroup!: FormGroup;
  store!: any;
  value: Signal<any> = input.required();

  // region Angular lifecycle hooks
  // endregion
}
