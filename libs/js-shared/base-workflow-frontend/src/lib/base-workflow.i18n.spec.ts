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
  PROCESS_DEFINITION_I18N_SCOPE,
  PROCESS_TASK_ASSIGNMENT_I18N_SCOPE,
  PROCESS_INSTANCE_I18N_SCOPE,
  TASK_DEFINITION_I18N_SCOPE,
  TASK_INPUT_REFERENCE_I18N_SCOPE,
  TASK_INSTANCE_I18N_SCOPE,
  TASK_OUTPUT_REFERENCE_I18N_SCOPE,
  TASK_STEP_DEFINITION_I18N_SCOPE,
  TASK_STEP_RESULT_I18N_SCOPE,
  TOOL_DEFINITION_I18N_SCOPE,
  TOOL_OPERATION_I18N_SCOPE,
  WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
} from './base-workflow.i18n';

const entityScopes = [
  PROCESS_DEFINITION_I18N_SCOPE,
  PROCESS_TASK_ASSIGNMENT_I18N_SCOPE,
  WORKFLOW_ROLE_DEFINITION_I18N_SCOPE,
  ARTIFACT_DEFINITION_I18N_SCOPE,
  TASK_DEFINITION_I18N_SCOPE,
  TASK_INPUT_REFERENCE_I18N_SCOPE,
  TASK_OUTPUT_REFERENCE_I18N_SCOPE,
  TASK_STEP_DEFINITION_I18N_SCOPE,
  TOOL_DEFINITION_I18N_SCOPE,
  TOOL_OPERATION_I18N_SCOPE,
  PROCESS_INSTANCE_I18N_SCOPE,
  TASK_INSTANCE_I18N_SCOPE,
  ARTIFACT_INSTANCE_I18N_SCOPE,
  TASK_STEP_RESULT_I18N_SCOPE,
];

const locales: Array<[string, Record<string, Record<string, string>>]> = [
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

  it('translates the same keys the English bundle does', () => {
    Object.entries(englishBundle as Record<string, Record<string, string>>).forEach(([blockName, englishBlock]) => {
      expect(Object.keys(bundle[blockName]).sort()).toEqual(Object.keys(englishBlock).sort());
    });
  });
});
