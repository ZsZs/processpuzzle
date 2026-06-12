import { inject, Injectable, Injector } from '@angular/core';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from './base-entity-facade-registry';

@Injectable({ providedIn: 'root' })
export class BaseEntityDescriptorRegistry {
  private readonly registry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private readonly injector = inject(Injector);

  getDescriptor(entityName: string | undefined): BaseEntityDescriptor | undefined {
    const facade = this.resolveFacade(entityName);
    return facade?.descriptor;
  }

  getStore<Store = unknown>(entityName: string | undefined): Store | undefined {
    const facade = this.resolveFacade(entityName);
    return facade?.store as Store | undefined;
  }

  private resolveFacade(entityName: string | undefined) {
    if (!entityName) return undefined;
    const facadeToken = this.registry[entityName];
    if (!facadeToken) return undefined;
    return this.injector.get(facadeToken, undefined);
  }
}
