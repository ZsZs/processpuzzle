import { describe, expect, it } from 'vitest';
import englishBundle from '../assets/i18n/base_workflow/en.json';
import germanBundle from '../assets/i18n/base_workflow/de.json';
import spanishBundle from '../assets/i18n/base_workflow/es.json';
import frenchBundle from '../assets/i18n/base_workflow/fr.json';
import hungarianBundle from '../assets/i18n/base_workflow/hu.json';
import {
  ARTIFACT_DEFINITION_I18N_SCOPE,
  ARTIFACT_INSTANCE_I18N_SCOPE,
  BASE_ENTITY_TRANSLOCO_SCOPE,
  BASE_WORKFLOW_TRANSLATION_SOURCE,
  BASE_WORKFLOW_TRANSLOCO_SCOPE,
  WORKFLOW_I18N_SCOPE,
  WORKFLOW_TASK_ASSIGNMENT_I18N_SCOPE,
  WORKFLOW_ROLE_USE_I18N_SCOPE,
  WORKFLOW_ARTIFACT_USE_I18N_SCOPE,
  WORKFLOW_TOOL_USE_I18N_SCOPE,
  WORKFLOW_REQUIRED_START_ARTIFACT_I18N_SCOPE,
  WORKFLOW_INSTANCE_I18N_SCOPE,
  TASK_DEFINITION_I18N_SCOPE,
  TASK_INSTANCE_I18N_SCOPE,
  TASK_STEP_DEFINITION_I18N_SCOPE,
  TASK_STEP_RESULT_I18N_SCOPE,
  TOOL_DEFINITION_I18N_SCOPE,
  TOOL_OPERATION_I18N_SCOPE,
  WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
  WORKFLOW_ROLE_MODELER_I18N_KEY,
} from './base-workflow.i18n';

/**
 * Dotted key paths of a bundle, as transloco flattens them under the scope alias — so a block with nested
 * keys (`workflow_role_definition.tabs.modeler`) is compared at the depth it is actually looked up at. A
 * top-level-keys-only comparison would pass a locale missing every nested key below a block it does have.
 */
function flattenKeys(translations: object, prefix = ''): string[] {
  return Object.entries(translations).flatMap(([key, value]) => (typeof value === 'object' && value !== null ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`]));
}

const entityScopes = [
  WORKFLOW_I18N_SCOPE,
  WORKFLOW_TASK_ASSIGNMENT_I18N_SCOPE,
  WORKFLOW_ROLE_USE_I18N_SCOPE,
  WORKFLOW_ARTIFACT_USE_I18N_SCOPE,
  WORKFLOW_TOOL_USE_I18N_SCOPE,
  WORKFLOW_REQUIRED_START_ARTIFACT_I18N_SCOPE,
  WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
  ARTIFACT_DEFINITION_I18N_SCOPE,
  TASK_DEFINITION_I18N_SCOPE,
  TASK_STEP_DEFINITION_I18N_SCOPE,
  TOOL_DEFINITION_I18N_SCOPE,
  TOOL_OPERATION_I18N_SCOPE,
  WORKFLOW_INSTANCE_I18N_SCOPE,
  TASK_INSTANCE_I18N_SCOPE,
  ARTIFACT_INSTANCE_I18N_SCOPE,
  TASK_STEP_RESULT_I18N_SCOPE,
];

/** A block holds attribute labels and, since the modeler, nested groups of screen labels beside them. */
type TranslationBlock = Record<string, string | Record<string, unknown>>;

const locales: Array<[string, Record<string, TranslationBlock>]> = [
  ['en', englishBundle],
  ['de', germanBundle],
  ['es', spanishBundle],
  ['fr', frenchBundle],
  ['hu', hungarianBundle],
];

describe('the library scope', () => {
  // The name base-workflow-backend already seeds — see default-translations/base-workflow.
  it('is the one the backend seeds bundles under', () => {
    expect(BASE_WORKFLOW_TRANSLOCO_SCOPE).toBe('base_workflow');
  });

  it('re-exports the framework scope, which every host of the generic screens has to register too', () => {
    expect(BASE_ENTITY_TRANSLOCO_SCOPE).toBe('base_entity');
  });

  it('roots every entity key under the library scope rather than giving it a scope of its own', () => {
    entityScopes.forEach((scope) => expect(scope.startsWith(`${BASE_WORKFLOW_TRANSLOCO_SCOPE}.`)).toBe(true));
  });

  it('names each entity scope once', () => {
    expect(new Set(entityScopes).size).toBe(entityScopes.length);
  });
});

describe('BASE_WORKFLOW_TRANSLATION_SOURCE', () => {
  // The backend fallback for a host that skips the asset copy. `segment` is the one
  // WorkflowTranslationEndpoint serves: /organizations/{orgKey}/workflow/translations/...
  it('points at base-workflow-backend’s own translations resource', () => {
    expect(BASE_WORKFLOW_TRANSLATION_SOURCE).toEqual({ scopes: ['base_workflow'], serviceRootKey: 'WORKFLOW_SERVICE_ROOT', segment: 'workflow' });
  });
});

describe.each(locales)('the %s bundle', (_locale, bundle) => {
  it('has a block for every entity scope this library declares', () => {
    const blockNames = entityScopes.map((scope) => scope.slice(`${BASE_WORKFLOW_TRANSLOCO_SCOPE}.`.length));

    expect(Object.keys(bundle).sort()).toEqual(blockNames.sort());
  });

  // `_self` is the reserved segment that keeps the entity's own display name in the same object as its
  // attribute labels without the two colliding.
  it('names every entity through the reserved _self key', () => {
    Object.values(bundle).forEach((block) => expect(block['_self']).toBeTruthy());
  });

  // Flattened, so a locale that has the `modeler` block but not the `modeler.empty` key inside it fails.
  it('translates the same keys the English bundle does', () => {
    expect(flattenKeys(bundle).sort()).toEqual(flattenKeys(englishBundle).sort());
  });

  it('leaves no key empty', () => {
    const value = (key: string) => key.split('.').reduce<unknown>((node, segment) => (node as Record<string, unknown>)[segment], bundle);

    expect(flattenKeys(bundle).every((key) => typeof value(key) === 'string' && (value(key) as string).trim().length > 0)).toBe(true);
  });

  // The label of the tab the roles branch mounts. Named here rather than left to the parity check, because
  // an untranslated tab renders its raw key in the tab bar.
  it('labels the Role Modeler tab', () => {
    expect(flattenKeys(bundle)).toContain(WORKFLOW_ROLE_MODELER_I18N_KEY.slice(`${BASE_WORKFLOW_TRANSLOCO_SCOPE}.`.length));
  });
});
