import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { TASK_STEP_DEFINITION_I18N_SCOPE } from '../../base-workflow.i18n';
import { TASK_DEFINITION_ENTITY_NAME, TASK_STEP_DEFINITION_ENTITY_NAME, TOOL_DEFINITION_ENTITY_NAME } from '../workflow-entity-names';

export { TASK_STEP_DEFINITION_ENTITY_NAME };

/** A `StepDefinition` is identified by its own `id`, unique within the task that declares it. */
export const TASK_STEP_DEFINITION_ID_FIELD = 'id';

function createStepDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique within the task, e.g. check-items';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // A `Tool Definition` is a routable aggregate of its own — `/tools`, shared across processes — so
  // this is a real reference the framework can resolve and navigate to, unlike the `refId` of a
  // task's inputs and outputs.
  const toolIdAttr = new BaseEntityAttrDescriptor('toolId', FormControlType.FOREIGN_KEY, 'Tool');
  toolIdAttr.linkedEntityType = TOOL_DEFINITION_ENTITY_NAME;

  // Plain text rather than a dropdown over the tool's operations: the options would have to come from
  // the store of *the tool chosen in the control above*, which a descriptor's static `selectables`
  // cannot express. The backend rejects an operation the tool does not declare.
  const toolOperationAttr = new BaseEntityAttrDescriptor('toolOperation', FormControlType.TEXT_BOX, 'Operation');
  toolOperationAttr.placeholder = 'Operation id within the tool, e.g. create-issue';

  // Both are `additionalProperties` by contract — PPCL expressions in, JSONPath expressions out — so
  // an open key/value editor is the only shape that can carry them without the form inventing a
  // closed list of context variables.
  const inputMappingAttr = new BaseEntityAttrDescriptor('inputMapping', FormControlType.ADDITIONAL_PROPERTIES, 'Input Mapping');
  inputMappingAttr.hideInTable = true;

  const outputMappingAttr = new BaseEntityAttrDescriptor('outputMapping', FormControlType.ADDITIONAL_PROPERTIES, 'Output Mapping');
  outputMappingAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const toolRow = new FlexboxDescriptor([toolIdAttr, toolOperationAttr], FlexDirection.ROW);
  toolRow.style = { 'column-gap': '10px' };
  const mappingRow = new FlexboxDescriptor([inputMappingAttr, outputMappingAttr], FlexDirection.ROW);
  mappingRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, descriptionAttr, toolRow, mappingRow], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createStepDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: TASK_STEP_DEFINITION_ENTITY_NAME,
    attrDescriptors: createStepDefinitionAttrDescriptors(),
    i18nScope: TASK_STEP_DEFINITION_I18N_SCOPE,
    componentParent: TASK_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
