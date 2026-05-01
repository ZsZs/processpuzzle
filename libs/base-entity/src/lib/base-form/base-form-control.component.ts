import { Component, computed, input, InputSignal } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';

@Component({
  standalone: true,
  template: ``,
  imports: [ReactiveFormsModule],
})
export abstract class BaseFormControlComponent<Entity extends BaseEntity> {
  config: InputSignal<BaseEntityAttrDescriptor> = input.required<BaseEntityAttrDescriptor>();
  entity: InputSignal<Entity> = input.required();
  formGroup!: FormGroup;
  store!: any;
  style = computed<{ [p: string]: any } | null | undefined>(() => this.config().style);
  value: InputSignal<any> = input.required();

  // region Angular lifecycle hooks
  // endregion
}
