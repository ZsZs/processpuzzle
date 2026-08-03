// Public API Surface of @processpuzzle/base-entity

export { AbstractAttrDescriptor, FormControlType } from './lib/base-entity/abstact-attr.descriptor';
export type { ArtifactAttr } from './lib/base-form/artifact/artifact-attr';
export { BaseEntityContainerComponent } from './lib/base-entity-container.component';
export { BaseEntityContainerStore } from './lib/base-entity-container.store'; // With @angular/build:ng-packagr executor causes a problem
export { BaseEntityRestService } from './lib/base-entity-service/base-entity-rest.service'; // With @angular/build:ng-packagr executor causes a problem
export type { BaseEntity, PersistedBaseEntity, PersistedEntity } from './lib/base-entity/base-entity';
export { BaseEntityAttrDescriptor, type Selectable, type SelectablesInput } from './lib/base-entity/base-entity-attr.descriptor';
export { BaseEntityFormComponent } from './lib/base-form/base-entity-form.component';
export { ComponentsListComponent } from './lib/base-form/components/components-list.component';
export { EmbeddedComponentsListComponent } from './lib/base-form/embedded-components/embedded-components-list.component';
export { EmbeddedComponentRefComponent } from './lib/base-form/embedded-components/embedded-component-ref.component';
export { RelatedEntitiesListComponent } from './lib/base-form/related-entities/related-entities-list.component';
export { BaseEntityDescriptor, type BaseEntityDescriptorOptions } from './lib/base-entity/base-entity.descriptor';
export { EntityLabelPipe } from './lib/i18n/entity-label.pipe';
export { BaseEntityListComponent, BASE_LIST_DESCRIPTORS } from './lib/base-list/base-entity-list.component';
export type { BaseEntityLoadResponse, BaseEntityQueryCondition, FilterCondition, OrderByCondition } from './lib/base-entity-service/base-entity-load-response';
export { OrderByDirection } from './lib/base-entity-service/base-entity-load-response';
export { SimpleEntityMapper, getEnumKeyByValue, getEnumValueByKey } from './lib/base-entity.mapper';
export type { BaseEntityMapper } from './lib/base-entity.mapper';
export { BaseEntityFacade, type EntityServiceKind } from './lib/base-entity-facade/base-entity-facade';
export { ACTIVE_ENTITY_FACADE } from './lib/base-entity-facade/active-entity-facade.token';
export { BaseEntityTabsStore } from './lib/base-tabs/base-entity-tabs.store';
export { BASE_ENTITY_SERVICE } from './lib/base-entity-service/base-entity.service';
export { BASE_ENTITY_STORE, BaseEntityStore } from './lib/base-entity-store/base-entity.store';
export { BASE_ENTITY_FACADE_REGISTRY, type BaseEntityFacadeRegistry, EntityRegistryComponent } from './lib/base-entity-facade/base-entity-facade-registry';
export { BaseEntityDescriptorRegistry } from './lib/base-entity-facade/base-entity-descriptor.registry';
export { EmbeddedEntityFacade } from './lib/base-entity-facade/embedded-entity.facade';
export { EmbeddedEntityService } from './lib/base-entity-service/embedded-entity.service';
export { EmbeddedAggregateAccessor, type ResolvedEmbeddedAggregate } from './lib/base-entity-embedded/embedded-aggregate.accessor';
export { embeddedAggregateGuard } from './lib/base-entity-embedded/embedded-aggregate.guard';
export type { EmbeddedRouteContext, EmbeddedRouteLevel } from './lib/base-entity-embedded/embedded-route-context';
export type { EmbeddedPath, EmbeddedPathStep, EmbeddedRow } from './lib/base-entity-embedded/embedded-aggregate';
export { BaseFormHostDirective } from './lib/base-form/base-form-host.directive';
export { BaseFormNavigatorSingletonStore, BaseFormNavigatorStore, RouteSegments, type NavigationState } from './lib/base-form-navigator/base-form-navigator.store';
export { NavigatorCommand, type NavigationPayload } from './lib/base-form-navigator/navigation-payload';
export { EntityRouteRegistry, ENTITY_NAME_ROUTE_DATA_KEY, EMBEDDED_ENTITY_ROUTE_DATA_KEY } from './lib/base-form-navigator/entity-route.registry';
export { provideEntityRouteRegistry } from './lib/base-form-navigator/entity-route-registry.providers';
export { BaseEntityFirestoreService } from './lib/base-entity-service/base-entity-firestore.service';
export { FlexboxDescriptor, FlexDirection } from './lib/base-entity/flexboxDescriptor';
export { Entity, type EntityOptions, getEntityOptions, Id, getEntityIds } from './lib/base-entity/decorators/entity.decorator';
export { BASE_ENTITY_ROUTES, baseEntityRoutes, type EmbeddedChildRoute } from './lib/base-entity.routes'; // With @angular/build:ng-packagr executor causes a problem
export { RULE_ENGINE, type EvaluatableRule, type RuleEngine, type RuleEvaluationResult, type RuleSeverity } from './lib/rule-engine/rule-engine';
export { RuleViolationsSingletonStore } from './lib/rule-engine/rule-violations.store';
export type { LookupTable } from './lib/base-form/lookup/lookup-table';
export { PdfExportService } from './lib/pdf-service/pdf-export.service';
export type { PdfColumnDefinition, PdfExportOptions, PdfExportResult } from './lib/pdf-service/pdf-export.types';
export { entityDescriptorToPdfColumns } from './lib/pdf-service/entity-descriptor-to-pdf-columns';
export { PdfExportOptionsDialog, type PdfExportDialogResult } from './lib/pdf-service/pdf-export-options.dialog';
