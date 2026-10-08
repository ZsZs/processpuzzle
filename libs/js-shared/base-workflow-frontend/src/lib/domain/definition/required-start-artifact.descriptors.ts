import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { WORKFLOW_REQUIRED_START_ARTIFACT_I18N_SCOPE } from '../../base-workflow.i18n';
import { ARTIFACT_DEFINITION_ENTITY_NAME, WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME, WORKFLOW_START_EVENT_ENTITY_NAME } from '../workflow-entity-names';

export { WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME };

/**
 * A `RequiredStartArtifact` has no `id` — the artifact it names is what identifies it within the start
 * event. The referencing attribute therefore has to set `referenceIdField`; see the
 * `requiredArtifacts` attribute of the `Workflow Start Event` descriptor.
 */
export const WORKFLOW_REQUIRED_START_ARTIFACT_ID_FIELD = 'artifactDefinitionId';

function createRequiredStartArtifactAttrDescriptors(): AbstractAttrDescriptor[] {
  // A real reference: the artifact is a catalog aggregate with a store of its own, so the framework
  // resolves its display name and navigates to it. The backend additionally refuses an artifact the
  // workflow has not declared in its own `artifacts`, which is wider than what this picker offers.
  const artifactDefinitionIdAttr = new BaseEntityAttrDescriptor('artifactDefinitionId', FormControlType.FOREIGN_KEY, 'Artifact', undefined, true);
  artifactDefinitionIdAttr.linkedEntityType = ARTIFACT_DEFINITION_ENTITY_NAME;
  artifactDefinitionIdAttr.required = true;
  artifactDefinitionIdAttr.isHeading = true;

  // Plain text rather than a picker: the state is named by the artifact's base-state machine, and
  // base-workflow records the name without resolving it — there is no store here to offer the states
  // of a machine chosen in another control. Absent means any state will do.
  const stateAttr = new BaseEntityAttrDescriptor('state', FormControlType.TEXT_BOX, 'State');
  stateAttr.placeholder = 'State the artifact has to be in, e.g. DRAFT; empty means any';

  const flexBoxContainer = new FlexboxDescriptor([artifactDefinitionIdAttr, stateAttr], FlexDirection.ROW);
  flexBoxContainer.style = { 'column-gap': '10px', width: 'fit-content' };
  return [flexBoxContainer];
}

/**
 * One artifact an `INPUT_ARTIFACT` start event waits for.
 *
 * Embedded in the start event — two levels below the workflow — because it is part of that event and
 * nothing else, and an entity of its own because `requiredArtifacts` is the one part of the event the
 * author edits row by row: `workflow/<id>/details/workflow-start-event/<eventId>/details/
 * workflow-required-start-artifact/<artifactId>/details`.
 */
export function createRequiredStartArtifactDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: WORKFLOW_REQUIRED_START_ARTIFACT_ENTITY_NAME,
    attrDescriptors: createRequiredStartArtifactAttrDescriptors(),
    i18nScope: WORKFLOW_REQUIRED_START_ARTIFACT_I18N_SCOPE,
    componentParent: WORKFLOW_START_EVENT_ENTITY_NAME,
    isEmbedded: true,
  });
}
