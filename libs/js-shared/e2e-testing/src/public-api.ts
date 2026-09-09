// Type-only re-exports from base-entity for consumer convenience.
// Runtime values (e.g. the FormControlType enum) are intentionally NOT re-exported,
// because base-entity is an Angular library whose evaluation requires the JIT compiler.
// In switch statements over `formControlType`, use string literals matching the enum values.
export type { BaseEntityDescriptor, BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';

// Control testers
export {
  ControlTester,
  RelationshipControlTester,
  ArtifactControlTester,
  createControlTester,
  controlTestersFor,
  relationshipTestersFor,
  artifactTestersFor,
  identificationAttrFromTesters,
  linkedFixtureAttrKey,
  parentReferenceAttrName,
  type ControlDataContext,
  type ControlInteractionContext,
  type FillControlOptions,
  type LinkedEntityFixture,
  type RelationshipKind,
} from './lib/controls/control-tester';

// Selectors
export { toTestId, attrSelector, blockingViolationsSelector, buttonTestId, buttonSelector, formControlSelector, formControlLocator } from './lib/selectors/selector.builder';
export { escapeRegExp, exactText } from './lib/selectors/text-match';

// Data
export {
  inputAttrs,
  identificationAttr,
  createControlDataContext,
  buildCreateData,
  buildCreateDataForContext,
  buildLinkedIdentifications,
  buildLinkedIdentificationsForContext,
  buildUpdateData,
  buildUpdateDataForContext,
} from './lib/data/test-data-factory';
export { EntityCrudFixtureManager, type EntityFixture } from './lib/data/entity-crud-fixture-manager';
export { resolveDependencyOrder } from './lib/data/dependency.resolver';
export { createPngBuffer, createTextBuffer } from './lib/data/binary-fixtures';

// Routing
export { RouteResolver, toRoutePath, entityIdFromDetailUrl } from './lib/routing/route.resolver';

// Setup
export { createGlobalSetup, type CreateGlobalSetupOptions } from './lib/setup/global-setup';

// Page objects
export { EntityListPO } from './lib/pages/entity-list.po';
export { EntityFormPO, type EntityFormContextOptions, type EntityFormPOOptions } from './lib/pages/entity-form.po';
export { RelationshipFieldsetPO, type RelationshipFieldsetPOOptions } from './lib/pages/relationship-fieldset.po';
export { ArtifactFieldsetPO, type ArtifactFieldsetPOOptions, type ArtifactUpload } from './lib/pages/artifact-fieldset.po';

// Suite factories
export { defineEntityListSuite, type DefineEntityListSuiteOptions } from './lib/suites/entity-list.suite';
export { defineEntityCrudSuite, type DefineEntityCrudSuiteOptions, type ExcludedEntity } from './lib/suites/entity-crud.suite';
export { defineEntityRelationshipSuite, type DefineEntityRelationshipSuiteOptions, type ExcludedRelationship } from './lib/suites/entity-relationship.suite';
export { defineEntityArtifactSuite, type DefineEntityArtifactSuiteOptions, type ExcludedArtifact } from './lib/suites/entity-artifact.suite';
