import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { TASK_INPUT_REFERENCE_I18N_SCOPE, TASK_OUTPUT_REFERENCE_I18N_SCOPE } from '../../base-workflow.i18n';
import { ReferenceType } from './task-definition';
import { TASK_DEFINITION_ENTITY_NAME, TASK_INPUT_REFERENCE_ENTITY_NAME, TASK_OUTPUT_REFERENCE_ENTITY_NAME } from '../workflow-entity-names';

export { TASK_INPUT_REFERENCE_ENTITY_NAME, TASK_OUTPUT_REFERENCE_ENTITY_NAME };

/**
 * A `TaskIOReference` has no `id` — the resource it points at is what identifies it, which is
 * `refId`. Referencing attributes therefore have to set `referenceIdField = 'refId'`; see the
 * `inputs` and `outputs` attributes of the `Task Definition` descriptor.
 */
export const TASK_IO_REFERENCE_ID_FIELD = 'refId';

const referenceTypeSelectables = toSelectables(Object.keys(ReferenceType));

/**
 * One factory for both, because the contract's `inputs` and `outputs` are the same schema and a
 * reference authored as an input has to behave exactly like one authored as an output. Only the
 * entity name, the placeholder and the transloco scope differ — which is enough for the two to be
 * separate entities in the registry, and so to have separate forms, without a second copy of this
 * layout to keep in step.
 */
function createTaskIOReferenceAttrDescriptors(direction: string): AbstractAttrDescriptor[] {
  // The kind of resource, and a closed list by contract. It comes first because it decides what a
  // `refId` even means: an artifact definition id, an entity definition id, a document id, or a key in
  // `WIDGET_REGISTRY`. `ARTIFACT` appears here without a line of its own — the selectables are built
  // from `Object.keys(ReferenceType)`, so adding the value to the model is what added it to the form.
  const typeAttr = new BaseEntityAttrDescriptor('type', FormControlType.DROPDOWN, 'Type', referenceTypeSelectables);
  typeAttr.required = true;

  // Plain text rather than a picker, and deliberately so: which store a reference resolves against
  // depends on the `type` chosen in the control above, so there is no single entity for a
  // `FOREIGN_KEY` to name — not even for `ARTIFACT`, since one control cannot switch its linked type
  // per row. The backend resolves it on save, and refuses an `ARTIFACT` the owning workflow has not
  // declared in its own `artifacts`.
  const refIdAttr = new BaseEntityAttrDescriptor('refId', FormControlType.TEXT_BOX, 'Reference', undefined, true);
  refIdAttr.required = true;
  refIdAttr.isHeading = true;
  refIdAttr.placeholder = `Id of the artifact, entity, document or widget this task ${direction}`;

  const labelAttr = new BaseEntityAttrDescriptor('label', FormControlType.TEXT_BOX, 'Label');
  labelAttr.placeholder = "Overrides the referenced resource's own name";

  const identityRow = new FlexboxDescriptor([typeAttr, refIdAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, labelAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createTaskInputReferenceDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: TASK_INPUT_REFERENCE_ENTITY_NAME,
    attrDescriptors: createTaskIOReferenceAttrDescriptors('reads'),
    i18nScope: TASK_INPUT_REFERENCE_I18N_SCOPE,
    componentParent: TASK_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}

export function createTaskOutputReferenceDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: TASK_OUTPUT_REFERENCE_ENTITY_NAME,
    attrDescriptors: createTaskIOReferenceAttrDescriptors('produces or modifies'),
    i18nScope: TASK_OUTPUT_REFERENCE_I18N_SCOPE,
    componentParent: TASK_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
