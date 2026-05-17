import { Component, computed, HostBinding, inject, Injector, input, InputSignal } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { NGXLogger } from 'ngx-logging-kit';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { BASE_ENTITY_FACADE_REGISTRY } from '../base-entity-facade/base-entity-facade-registry';
import { createTestId } from '../base-entity/base-entity-utility';

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
  linkedEntityType = computed<BaseEntityDescriptor>(() => {
    const linkedEntityType = this.config().linkedEntityType;
    if (linkedEntityType === undefined) {
      this.logger.error(`linkedEntityType should be defined for '${this.config().attrName}'.`);
      throw new Error(`linkedEntityType should be defined for '${this.config().attrName}'.`);
    }
    return linkedEntityType;
  });
  value: InputSignal<any> = input.required();

  @HostBinding('attr.data-testid')
  get testId(): string | null {
    const attrName = this.config()?.attrName;
    const entity = this.entity();
    if (!attrName || !entity) return null;
    return createTestId(entity.constructor as new () => Entity, attrName);
  }

  protected readonly logger = inject(NGXLogger);
  protected readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly injector = inject(Injector);
  private readonly facadeRegistry = inject(BASE_ENTITY_FACADE_REGISTRY);

  // region Angular lifecycle hooks
  // endregion

  protected resolveRelatedEntityStore<Store extends object>(): Store | undefined {
    const entityName = this.config().linkedEntityType?.entityName;
    const facadeToken = entityName ? this.facadeRegistry[entityName] : undefined;
    if (facadeToken) {
      const facade = this.injector.get(facadeToken, undefined);
      return facade?.store as Store | undefined;
    }

    const store = this.config().linkedEntityType?.store;
    if (!store) {
      return undefined;
    }

    if (this.isStoreInstance(store)) {
      return store as Store;
    }

    try {
      return this.injector.get(store, undefined) as Store | undefined;
    } catch {
      return undefined;
    }
  }

  private isStoreInstance(store: unknown): boolean {
    return !!store && (typeof store === 'object' || typeof store === 'function') && ('load' in store || 'loadById' in store || 'entities' in store);
  }
}
