import type { TranslationSource } from '@processpuzzle/util';

/**
 * Transloco scope of this library. The translations live in
 * `libs/js-shared/base-workflow-frontend/src/assets/i18n/base_workflow/*.json` and are published with
 * the package; a consuming application copies them to `assets/i18n/base_workflow` (see the testbed's
 * `project.json`). The scope is registered on {@link BASE_WORKFLOW_ROUTES}, so it loads lazily with
 * the route rather than with the application shell.
 *
 * The alias is spelled out wherever this is registered, on purpose: transloco camel-cases the default
 * alias, which would turn `base_workflow` into `baseWorkflow` and silently miss every key below.
 *
 * The name matches what base-workflow-backend already seeds — see
 * `default-translations/base-workflow/processpuzzle-testbed-translations.yaml`, whose bundles were
 * written before this library had a scope to load them into.
 */
export const BASE_WORKFLOW_TRANSLOCO_SCOPE = 'base_workflow';

/**
 * Scope of the generic framework labels (`base_entity.tabs.*`, `base_entity.toolbar.*`), whose files
 * base-entity owns. It has to be registered next to {@link BASE_WORKFLOW_TRANSLOCO_SCOPE} wherever
 * the generic screens are hosted: `BaseEntityTabsComponent` translates from it through
 * `TranslocoPipe`, which caches the value it resolved for a key on first render. If only
 * `base_workflow` were loaded, the tabs would keep the raw key even after the toolbar — which
 * registers this scope for itself — triggers the load.
 */
export const BASE_ENTITY_TRANSLOCO_SCOPE = 'base_entity';

// region definition layer
/** Key root of the `Process Definition` entity name (`._self`) and of its attribute labels. */
export const PROCESS_DEFINITION_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.process_definition`;

/**
 * Key roots of the process's own embedded row, of the three catalog aggregates it references, and of
 * the rows nested one level deeper inside a task. They are children of
 * {@link BASE_WORKFLOW_TRANSLOCO_SCOPE} rather than scopes of their own, because the whole graph is
 * edited under {@link BASE_WORKFLOW_ROUTES} and one scope registration has to cover all of it — the
 * catalog and embedded route branches add none.
 */
export const PROCESS_TASK_ASSIGNMENT_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.process_task_assignment`;
export const WORKFLOW_ROLE_DEFINITION_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.workflow_role_definition`;
export const ARTIFACT_DEFINITION_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.artifact_definition`;
export const TASK_DEFINITION_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.task_definition`;
export const TASK_INPUT_REFERENCE_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.task_input_reference`;
export const TASK_OUTPUT_REFERENCE_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.task_output_reference`;
export const TASK_STEP_DEFINITION_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.task_step_definition`;

/** Key root of the `Tool Definition` aggregate and of the operations embedded in it. */
export const TOOL_DEFINITION_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.tool_definition`;
export const TOOL_OPERATION_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.tool_operation`;
// endregion

// region execution layer
/** Key roots of the run-time side: an instance and the task, artifact and step rows below it. */
export const PROCESS_INSTANCE_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.process_instance`;
export const TASK_INSTANCE_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.task_instance`;
export const ARTIFACT_INSTANCE_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.artifact_instance`;
export const TASK_STEP_RESULT_I18N_SCOPE = `${BASE_WORKFLOW_TRANSLOCO_SCOPE}.task_step_result`;
// endregion

/**
 * Where this library's transloco bundles come from when the application ships without its assets.
 *
 * Spread into the application's `TRANSLATION_SOURCE_REGISTRY`, the way its facades are spread into
 * `BASE_ENTITY_FACADE_REGISTRY`. The scope normally arrives as static files — the build copies them
 * into `assets/i18n/base_workflow` and the loader tries the asset first — so this is reached only by
 * a host that skips that copy step. `segment` is base-workflow-backend's own translations resource
 * (`/organizations/{orgKey}/workflow/translations/...`, see `WorkflowTranslationEndpoint`).
 */
export const BASE_WORKFLOW_TRANSLATION_SOURCE: TranslationSource = {
  scopes: [BASE_WORKFLOW_TRANSLOCO_SCOPE],
  serviceRootKey: 'WORKFLOW_SERVICE_ROOT',
  segment: 'workflow',
};
