// Type-only re-exports from base-entity for consumer convenience.
// Runtime values (e.g. the FormControlType enum) are intentionally NOT re-exported,
// because base-entity is an Angular library whose evaluation requires the JIT compiler.
// In switch statements over `formControlType`, use string literals matching the enum values.
export type { BaseEntityDescriptor, BaseEntityAttrDescriptor, FormControlType } from '@processpuzzle/base-entity';

// Selectors
export { toTestId, attrSelector, buttonTestId, buttonSelector, formControlSelector, formControlLocator } from './lib/selectors/selector.builder';

// Data
export { inputAttrs, identificationAttr, buildCreateData, buildUpdateData } from './lib/data/test-data-factory';
export { resolveDependencyOrder } from './lib/data/dependency.resolver';

// Routing
export { RouteResolver, toRoutePath } from './lib/routing/route.resolver';

// Setup
export { createGlobalSetup, type CreateGlobalSetupOptions } from './lib/setup/global-setup';

// Page objects
export { EntityListPO } from './lib/pages/entity-list.po';
export { EntityFormPO } from './lib/pages/entity-form.po';

// Suite factories
export { defineEntityListSuite, type DefineEntityListSuiteOptions } from './lib/suites/entity-list.suite';
export { defineEntityCrudSuite, type DefineEntityCrudSuiteOptions } from './lib/suites/entity-crud.suite';
