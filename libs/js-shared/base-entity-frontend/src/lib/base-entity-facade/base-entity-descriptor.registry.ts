import { inject, Injectable, Injector } from '@angular/core';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { BASE_ENTITY_FACADE_REGISTRY } from './base-entity-facade-registry';

@Injectable({ providedIn: 'root' })
export class BaseEntityDescriptorRegistry {
  private readonly registry = inject(BASE_ENTITY_FACADE_REGISTRY);
  private readonly injector = inject(Injector);

  getDescriptor(entityName: string | undefined): BaseEntityDescriptor | undefined {
    return this.resolveFacade(entityName)?.descriptor;
  }

  /**
   * An embedded entity has a store too — see `EmbeddedEntityFacade`. It reads and writes the containing
   * entity's document rather than an endpoint of its own, which is invisible from here.
   */
  getStore<Store = unknown>(entityName: string | undefined): Store | undefined {
    return this.resolveFacade(entityName)?.store as Store | undefined;
  }

  private resolveFacade(entityName: string | undefined) {
    if (!entityName) return undefined;
    const facadeToken = this.registry[entityName];
    if (!facadeToken) return undefined;
    return this.injector.get(facadeToken, null, { optional: true }) ?? undefined;
  }
}
