import { Component, computed, inject, Injector, input, InputSignal } from '@angular/core';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { BaseEntity } from '../base-entity/base-entity';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseFormNavigatorSingletonStore } from '../base-form-navigator/base-form-navigator.store';
import { BASE_ENTITY_STORE_REGISTRY } from '../base-entity-store/base-entity-store-registry';

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
  protected readonly formNavigator = inject(BaseFormNavigatorSingletonStore);
  private readonly injector = inject(Injector);
  private readonly storeRegistry = inject(BASE_ENTITY_STORE_REGISTRY);

  // region Angular lifecycle hooks
  // endregion

  protected resolveRelatedEntityStore<Store extends object>(): Store | undefined {
    const entityName = this.config().linkedEntityType?.entityName;
    const registryStoreToken = entityName ? this.storeRegistry[entityName] : undefined;
    if (registryStoreToken) {
      return this.injector.get(registryStoreToken, undefined) as Store | undefined;
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
    return (
      !!store &&
      (typeof store === 'object' || typeof store === 'function') &&
      ('load' in store || 'loadById' in store || 'entities' in store)
    );
  }
}
