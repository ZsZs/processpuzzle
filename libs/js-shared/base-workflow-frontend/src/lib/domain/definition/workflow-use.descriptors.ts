import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { WORKFLOW_ARTIFACT_USE_I18N_SCOPE, WORKFLOW_ROLE_USE_I18N_SCOPE, WORKFLOW_TOOL_USE_I18N_SCOPE } from '../../base-workflow.i18n';
import {
  ARTIFACT_DEFINITION_ENTITY_NAME,
  TOOL_DEFINITION_ENTITY_NAME,
  WORKFLOW_ARTIFACT_USE_ENTITY_NAME,
  WORKFLOW_ENTITY_NAME,
  WORKFLOW_ROLE_DEFINITION_ENTITY_NAME,
  WORKFLOW_ROLE_USE_ENTITY_NAME,
  WORKFLOW_TOOL_USE_ENTITY_NAME,
} from '../workflow-entity-names';

export { WORKFLOW_ARTIFACT_USE_ENTITY_NAME, WORKFLOW_ROLE_USE_ENTITY_NAME, WORKFLOW_TOOL_USE_ENTITY_NAME };

/**
 * A `*Use` has no `id` — the definition it points at is what identifies it within the workflow.
 * Referencing attributes therefore have to set `referenceIdField`; see the `roles`, `artifacts` and
 * `tools` attributes of the `Workflow` descriptor. Same arrangement as
 * `WORKFLOW_TASK_ASSIGNMENT_ID_FIELD`.
 */
export const WORKFLOW_ROLE_USE_ID_FIELD = 'roleDefinitionId';
export const WORKFLOW_ARTIFACT_USE_ID_FIELD = 'artifactDefinitionId';
export const WORKFLOW_TOOL_USE_ID_FIELD = 'toolDefinitionId';

/**
 * One factory for all three, because a `RoleUse`, an `ArtifactUse` and a `ToolUse` are the same schema
 * over a different target: an object wrapping one definition id. Only the attribute name, the linked
 * entity and the label differ — which is enough for the three to be separate entities in the registry,
 * and so to have separate forms, without three copies of this layout to keep in step. Same idiom as
 * the single factory behind a task's inputs and outputs before those became id lists.
 *
 * The one field is a `FOREIGN_KEY` rather than a text box, and that is the whole point of modelling a
 * `*Use` as an entity: the control resolves the definition's display name through its store, offers
 * the tenant's catalog to pick from, and renders a link icon that navigates to the definition itself.
 * `isHeading` and `isLinkToDetails`, because the definition is what this row *is* — there is no
 * separate name to head it with.
 */
function createWorkflowUseAttrDescriptors(attrName: string, linkedEntityType: string, label: string): AbstractAttrDescriptor[] {
  const definitionIdAttr = new BaseEntityAttrDescriptor(attrName, FormControlType.FOREIGN_KEY, label, undefined, true);
  definitionIdAttr.linkedEntityType = linkedEntityType;
  definitionIdAttr.required = true;
  definitionIdAttr.isHeading = true;

  const flexBoxContainer = new FlexboxDescriptor([definitionIdAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * The role's participation in one workflow.
 *
 * Embedded rather than a resource of its own — and rather than an entry in an id list — because the
 * contract says `Workflow.roles` is `RoleUse[]`: the row travels inside the workflow's payload, is
 * addressed through it (`workflow/order-fulfillment-workflow/details/workflow-role-use/clerk/details`),
 * and is where per-workflow configuration of a shared role will go when there is any.
 */
export function createWorkflowRoleUseDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_ROLE_USE_ENTITY_NAME,
    attrDescriptors: createWorkflowUseAttrDescriptors(WORKFLOW_ROLE_USE_ID_FIELD, WORKFLOW_ROLE_DEFINITION_ENTITY_NAME, 'Role'),
    i18nScope: WORKFLOW_ROLE_USE_I18N_SCOPE,
    componentParent: WORKFLOW_ENTITY_NAME,
    isEmbedded: true,
  });
}

/** The artifact's participation in one workflow. Every artifact its tasks touch has to be declared here. */
export function createWorkflowArtifactUseDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_ARTIFACT_USE_ENTITY_NAME,
    attrDescriptors: createWorkflowUseAttrDescriptors(WORKFLOW_ARTIFACT_USE_ID_FIELD, ARTIFACT_DEFINITION_ENTITY_NAME, 'Artifact'),
    i18nScope: WORKFLOW_ARTIFACT_USE_I18N_SCOPE,
    componentParent: WORKFLOW_ENTITY_NAME,
    isEmbedded: true,
  });
}

/** The tool's availability in one workflow. A task step may only invoke a tool declared here. */
export function createWorkflowToolUseDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_TOOL_USE_ENTITY_NAME,
    attrDescriptors: createWorkflowUseAttrDescriptors(WORKFLOW_TOOL_USE_ID_FIELD, TOOL_DEFINITION_ENTITY_NAME, 'Tool'),
    i18nScope: WORKFLOW_TOOL_USE_I18N_SCOPE,
    componentParent: WORKFLOW_ENTITY_NAME,
    isEmbedded: true,
  });
}
