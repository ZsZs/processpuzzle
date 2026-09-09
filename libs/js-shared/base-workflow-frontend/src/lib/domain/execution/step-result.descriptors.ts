import { AbstractAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { TASK_STEP_RESULT_I18N_SCOPE } from '../../base-workflow.i18n';
import { TASK_INSTANCE_ENTITY_NAME, TASK_STEP_RESULT_ENTITY_NAME } from '../workflow-entity-names';
import { readOnlyAttr } from './read-only-attr';

export { TASK_STEP_RESULT_ENTITY_NAME };

/** A `StepResult` has no `id` — the step it reports on identifies it, which is `stepId`. */
export const TASK_STEP_RESULT_ID_FIELD = 'stepId';

function createStepResultAttrDescriptors(): AbstractAttrDescriptor[] {
  const stepIdAttr = readOnlyAttr('stepId', FormControlType.TEXT_BOX, 'Step', undefined, true);
  stepIdAttr.isHeading = true;

  const completedAtAttr = readOnlyAttr('completedAt', FormControlType.TEXT_BOX, 'Completed At');

  // The raw body the tool answered with. An open map is the only shape that can carry it; the control
  // renders every value as text, so a nested object shows as `[object Object]` — acceptable for a
  // read-only trace, and the value itself round-trips untouched.
  const toolResponseAttr = readOnlyAttr('toolResponse', FormControlType.ADDITIONAL_PROPERTIES, 'Tool Response');
  toolResponseAttr.hideInTable = true;

  const errorAttr = readOnlyAttr('error', FormControlType.TEXTAREA, 'Error');
  errorAttr.styleClass = 'full-width';

  const identityRow = new FlexboxDescriptor([stepIdAttr, completedAtAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, errorAttr, toolResponseAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createStepResultDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: TASK_STEP_RESULT_ENTITY_NAME,
    attrDescriptors: createStepResultAttrDescriptors(),
    i18nScope: TASK_STEP_RESULT_I18N_SCOPE,
    componentParent: TASK_INSTANCE_ENTITY_NAME,
    isEmbedded: true,
    // Read-only by contract — see `readOnlyAttr` for what the two levers do.
    isAbstract: true,
  });
}
