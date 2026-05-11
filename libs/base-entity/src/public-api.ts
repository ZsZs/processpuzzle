// Public API Surface of @processpuzzle/base-entity

export { AbstractAttrDescriptor, FormControlType } from './lib/base-entity/abstact-attr.descriptor';
export { ArtifactAttr } from './lib/base-form/artifact/artifact-attr';
export { BaseEntityContainerComponent } from './lib/base-entity-container.component';
export { BaseEntityContainerStore } from './lib/base-entity-container.store'; // With @angular/build:ng-packagr executor causes a problem
export { BaseEntityRestService } from './lib/base-entity-service/base-entity-rest.service'; // With @angular/build:ng-packagr executor causes a problem
export type { BaseEntity } from './lib/base-entity/base-entity';
export { BaseEntityAttrDescriptor } from './lib/base-entity/base-entity-attr.descriptor';
export { BaseEntityFormComponent } from './lib/base-form/base-entity-form.component';
export { BaseEntityDescriptor, type BaseEntityDescriptorOptions } from './lib/base-entity/base-entity.descriptor';
export { BaseEntityListComponent, BASE_LIST_DESCRIPTORS } from './lib/base-list/base-entity-list.component';
export type { BaseEntityLoadResponse, BaseEntityQueryCondition, FilterCondition, OrderByCondition } from './lib/base-entity-service/base-entity-load-response';
export { OrderByDirection } from './lib/base-entity-service/base-entity-load-response';
export { BaseEntityMapper, getEnumKeyByValue, getEnumValueByKey } from './lib/base-entity.mapper';
export { BaseEntityTabsStore } from './lib/base-tabs/base-entity-tabs.store';
export { BASE_ENTITY_SERVICE } from './lib/base-entity-service/base-entity.service';
export { BASE_ENTITY_STORE, BaseEntityStore } from './lib/base-entity-store/base-entity.store';
export { BASE_ENTITY_STORE_REGISTRY, type BaseEntityStoreRegistry } from './lib/base-entity-store/base-entity-store-registry';
export { BaseFormHostDirective } from './lib/base-form/base-form-host.directive';
export { BaseFormNavigatorSingletonStore, BaseFormNavigatorStore, RouteSegments, type NavigationState } from './lib/base-form-navigator/base-form-navigator.store';
export { BaseEntityFirestoreService } from './lib/base-entity-service/base-entity-firestore.service';
export { FlexboxDescriptor, FlexDirection } from './lib/base-entity/flexboxDescriptor';
export { Entity, type EntityOptions, getEntityOptions, Id, getEntityIds } from './lib/base-entity/decorators/entity.decorator';
export { BASE_ENTITY_ROUTES } from './lib/base-entity.routes'; // With @angular/build:ng-packagr executor causes a problem
export { LookupTable } from './lib/base-form/lookup/lookup-table';
