//import { BaseEntity } from './base-entity/base-entity';

// function entityName<Entity extends BaseEntity>(entityType: new () => Entity) {
//   const entity = new entityType();
//   return entity.constructor.name;
// }

//export const componentStore = <Entity extends BaseEntity>(entityType: new () => Entity, repository: ProviderToken<BaseEntityService<Entity>>) =>
//  signalStore({ providedIn: 'root' }, BaseEntityStore(entityType, repository), BaseFormNavigatorStore(entityName<Entity>(entityType)), BaseEntityTabsStore(), BaseEntityContainerStore());

//export type COMPONENT_ENTITY_STORE_TYPE = Type<typeof componentStore>;
//export const COMPONENT_ENTITY_STORE = new InjectionToken<typeof componentStore>('COMPONENT_ENTITY_STORE');
