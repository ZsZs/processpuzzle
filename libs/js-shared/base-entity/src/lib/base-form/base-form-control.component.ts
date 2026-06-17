import { Component, computed, HostBinding, inject, input, InputSignal } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { NGXLogger } from 'ngx-logging-kit';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { BaseEntityDescriptorRegistry } from '../base-entity-facade/base-entity-descriptor.registry';
import { createTestId } from '../base-entity/base-entity-utility';
import { BaseEntityStoreApi } from '../base-entity-store/base-entity.store';

@Component({
  standalone: true,
  template: ``,
  imports: [ReactiveFormsModule],
})
export abstract class BaseFormControlComponent<Entity extends BaseEntity> {
  config: InputSignal<BaseEntityAttrDescriptor> = input.required<BaseEntityAttrDescriptor>();
  entity: InputSignal<Entity> = input.required();
  entityName: InputSignal<string> = input.required<string>();
  formGroup!: FormGroup;
  store!: BaseEntityStoreApi<Entity>;
  style = computed<{ [p: string]: unknown } | null | undefined>(() => this.config().style);
  linkedEntityName = computed<string>(() => {
    const linkedEntityName = this.config().linkedEntityType;
    if (linkedEntityName === undefined) {
      this.logger.error(`linkedEntityType should be defined for '${this.config().attrName}'.`);
      throw new Error(`linkedEntityType should be defined for '${this.config().attrName}'.`);
    }
    return linkedEntityName;
  });
  linkedEntityDescriptor = computed<BaseEntityDescriptor | undefined>(() => this.descriptorRegistry.getDescriptor(this.linkedEntityName()));
  value: InputSignal<unknown> = input.required();

  @HostBinding('attr.data-testid')
  get testId(): string | null {
    const attrName = this.config()?.attrName;
    const entityName = this.entityName();
    if (!attrName || !entityName) return null;
    return createTestId(entityName, attrName);
  }

  protected readonly logger = inject(NGXLogger);
  protected readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  protected readonly descriptorRegistry = inject(BaseEntityDescriptorRegistry);

  // region Angular lifecycle hooks
  // endregion
}
