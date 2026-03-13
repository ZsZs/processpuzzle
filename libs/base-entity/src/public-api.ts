// Public API Surface of @processpuzzle/base-entity

export { AbstractAttrDescriptor, FormControlType } from './lib/base-entity/abstact-attr.descriptor';
export { BaseEntityContainerStore } from './lib/base-entity-container.store';
export type { BaseEntity } from './lib/base-entity/base-entity';
export { BaseEntityAttrDescriptor } from './lib/base-entity/base-entity-attr.descriptor';
export { BaseEntityFormComponent } from './lib/base-form/base-entity-form.component';
export type { BaseEntityDescriptor } from './lib/base-entity/base-entity.descriptor';
export { BaseEntityListComponent, BASE_LIST_DESCRIPTORS } from './lib/base-list/base-entity-list.component';
export type { BaseEntityLoadResponse, BaseEntityQueryCondition, FilterCondition, OrderByCondition } from './lib/base-entity-service/base-entity-load-response';
export { OrderByDirection } from './lib/base-entity-service/base-entity-load-response';
export { BaseEntityMapper } from './lib/base-entity.mapper';
export { BaseEntityTabsStore } from './lib/base-tabs/base-entity-tabs.store';
export { BASE_ENTITY_SERVICE } from './lib/base-entity-service/base-entity.service';
export { BASE_ENTITY_STORE, BaseEntityStore } from './lib/base-entity-store/base-entity.store';
export { BaseFormHostDirective } from './lib/base-form/base-form-host.directive';
export { BaseFormNavigatorStore, RouteSegments, type NavigationState } from './lib/base-form-navigator/base-form-navigator.store';
export { BaseEntityFirestoreService } from './lib/base-entity-service/base-entity-firestore.service';
export { FlexboxDescriptor, FlexDirection } from './lib/base-entity/flexboxDescriptor';
export { Entity, type EntityOptions, getEntityOptions, Id, getEntityIds } from './lib/base-entity/decorators/entity.decorator';
export { BASE_ENTITY_ROUTES } from './lib/base-entity.routes';
