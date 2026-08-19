// Public API Surface of @processpuzzle/base-entity

export { AbstractAttrDescriptor, FormControlType } from './lib/base-entity/abstact-attr.descriptor';
export type { ArtifactAttr } from './lib/base-form/artifact/artifact-attr';
export { BaseEntityContainerComponent } from './lib/base-entity-container.component';
export { BaseEntityContainerStore } from './lib/base-entity-container.store'; // With @angular/build:ng-packagr executor causes a problem
export { BaseEntityRestService } from './lib/base-entity-service/base-entity-rest.service'; // With @angular/build:ng-packagr executor causes a problem
export type { BaseEntity, PersistedBaseEntity, PersistedEntity } from './lib/base-entity/base-entity';
export { BaseEntityAttrDescriptor, type Selectable, type SelectablesInput } from './lib/base-entity/base-entity-attr.descriptor';
export { toSelectables } from './lib/base-entity/selectables';
export { BaseEntityFormComponent } from './lib/base-form/base-entity-form.component';
export { ComponentsListComponent } from './lib/base-form/components/components-list.component';
export { EmbeddedComponentsListComponent } from './lib/base-form/embedded-components/embedded-components-list.component';
export { EmbeddedComponentRefComponent } from './lib/base-form/embedded-components/embedded-component-ref.component';
export { RelatedEntitiesListComponent } from './lib/base-form/related-entities/related-entities-list.component';
export { BaseEntityDescriptor, type BaseEntityDescriptorOptions, type EntityTabDescriptor } from './lib/base-entity/base-entity.descriptor';
export { EntityLabelPipe, translateLabel } from './lib/i18n/entity-label.pipe';
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

// The metadata layer: an entity type declared as a `BaseEntityDefinition` row rather than as a class with a
// facade beside it. `EntityScreenResolver` is what a run-time shell asks; the rest is exported for the
// designer, which edits the definitions this reads.
export type { DynamicEntity, EntityAttributeDefinition, EntityDefinition, EntityDefinitionStatus, EntityValueKind } from './lib/base-entity-definition/entity-definition';
export { ENTITY_SERVICE_ROOT_KEY, EntityDefinitionService } from './lib/base-entity-definition/entity-definition.service';
export { EntityDefinitionRegistry } from './lib/base-entity-definition/entity-definition.registry';
export { controlTypeOf, descriptorOf, referenceIdFieldOf, type DefinitionLookup } from './lib/base-entity-definition/dynamic-entity.descriptor';
export { DynamicEntityMapper } from './lib/base-entity-definition/dynamic-entity.mapper';
export { DynamicEntityService } from './lib/base-entity-definition/dynamic-entity.service';
export { DynamicEmbeddedEntityFacade, DynamicEntityFacade, dynamicEntityTypeOf } from './lib/base-entity-definition/dynamic-entity.facade';
export { DynamicEntityRegistry, type ResolvedDynamicEntity } from './lib/base-entity-definition/dynamic-entity.registry';

// Mounting an entity's generated screens at a route — List and Details with the tabs, toolbar and status bar.
// The seam a host application uses, whether its entities are compiled in or defined as metadata: base-app is
// one caller of this, not a prerequisite for it.
export { EntityScreenResolver, type EntityScreens } from './lib/base-entity-screens/entity-screens.resolver';
export { entityScreenRoute, type EntityScreenRouteOptions } from './lib/base-entity-screens/entity-screen-routes';
export { BaseEntityScreensComponent, ENTITY_DESCRIPTOR_ROUTE_DATA_KEY, REQUESTED_ENTITY_ROUTE_DATA_KEY } from './lib/base-entity-screens/entity-screens.component';
export { EmbeddedEntityFacade } from './lib/base-entity-facade/embedded-entity.facade';
export { EmbeddedEntityService } from './lib/base-entity-service/embedded-entity.service';
export { EmbeddedAggregateAccessor, type ResolvedEmbeddedAggregate } from './lib/base-entity-embedded/embedded-aggregate.accessor';
export { embeddedAggregateGuard } from './lib/base-entity-embedded/embedded-aggregate.guard';
export { EmbeddedEntityHostComponent } from './lib/base-entity-embedded/embedded-entity-host.component';
export type { EmbeddedBreadcrumbLevel, EmbeddedRouteContext, EmbeddedRouteLevel } from './lib/base-entity-embedded/embedded-route-context';
export type { EmbeddedPath, EmbeddedPathStep, EmbeddedRow } from './lib/base-entity-embedded/embedded-aggregate';
export { BaseFormHostDirective } from './lib/base-form/base-form-host.directive';
export { BaseFormNavigatorSingletonStore, BaseFormNavigatorStore, RouteSegments, snakeCaseName, type NavigationState } from './lib/base-form-navigator/base-form-navigator.store';
export { NavigatorCommand, type NavigationPayload } from './lib/base-form-navigator/navigation-payload';
export { EntityRouteRegistry, ENTITY_NAME_ROUTE_DATA_KEY, EMBEDDED_ENTITY_ROUTE_DATA_KEY } from './lib/base-form-navigator/entity-route.registry';
export { provideEntityRouteRegistry } from './lib/base-form-navigator/entity-route-registry.providers';
export { BaseEntityFirestoreService } from './lib/base-entity-service/base-entity-firestore.service';
export { FlexboxDescriptor, FlexDirection } from './lib/base-entity/flexboxDescriptor';
export { Entity, type EntityOptions, getEntityOptions, Id, getEntityIds } from './lib/base-entity/decorators/entity.decorator';
export { BASE_ENTITY_ROUTES, baseEntityRoutes, type EmbeddedChildRoute } from './lib/base-entity.routes'; // With @angular/build:ng-packagr executor causes a problem
// Exported so a feature contributing an `EntityTabDescriptor` with `canMatch` can name the route parameter
// this library puts in the path, rather than restating the literal `'entityId'` and drifting from it.
export { BaseUrlSegments } from './lib/base-form-navigator/base-url-segments';
export { RULE_ENGINE, type EvaluatableRule, type RuleEngine, type RuleEvaluationResult, type RuleSeverity } from './lib/rule-engine/rule-engine';
export { RuleViolationsSingletonStore } from './lib/rule-engine/rule-violations.store';
export type { LookupTable } from './lib/base-form/lookup/lookup-table';
export { PdfExportService } from './lib/pdf-service/pdf-export.service';
export type { PdfColumnDefinition, PdfExportOptions, PdfExportResult } from './lib/pdf-service/pdf-export.types';
export { entityDescriptorToPdfColumns } from './lib/pdf-service/entity-descriptor-to-pdf-columns';
export { PdfExportOptionsDialog, type PdfExportDialogResult } from './lib/pdf-service/pdf-export-options.dialog';
export { BASE_ENTITY_TRANSLATION_SOURCE, BASE_ENTITY_TRANSLOCO_SCOPE } from './lib/i18n/base-entity.i18n';
