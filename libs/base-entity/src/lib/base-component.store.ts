import { InjectionToken, ProviderToken, Type } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { BaseEntity } from './base-entity/base-entity';
import { BaseEntityService } from './base-entity.service';
import { BaseEntityStore } from './base-entity.store';
import { BaseFormNavigatorStore } from './base-form-navigator/base-form-navigator.store';
import { BaseEntityTabsStore } from './base-tabs/base-entity-tabs.store';
import { BaseEntityContainerStore } from './base-entity-container.store';

export const componentStore = <Entity extends BaseEntity>(entityType: { new (): Entity }, repository: ProviderToken<BaseEntityService<Entity>>) =>
  signalStore({ providedIn: 'root' }, BaseEntityStore(entityType, repository), BaseFormNavigatorStore(entityType), BaseEntityTabsStore(), BaseEntityContainerStore());

export type COMPONENT_ENTITY_STORE_TYPE = Type<typeof componentStore>;
export const COMPONENT_ENTITY_STORE = new InjectionToken<typeof componentStore>('COMPONENT_ENTITY_STORE');
