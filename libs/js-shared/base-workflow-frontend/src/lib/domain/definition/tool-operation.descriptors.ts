import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { TOOL_OPERATION_I18N_SCOPE } from '../../base-workflow.i18n';
import { HttpMethod } from './tool-definition';
import { TOOL_DEFINITION_ENTITY_NAME, TOOL_OPERATION_ENTITY_NAME } from '../workflow-entity-names';

export { TOOL_OPERATION_ENTITY_NAME };

/** A `ToolOperation` is identified by its own `id`, unique within the tool — what `toolOperation` names. */
export const TOOL_OPERATION_ID_FIELD = 'id';

const httpMethodSelectables = toSelectables(Object.keys(HttpMethod));

function createToolOperationAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique within the tool, e.g. create-issue';

  const methodAttr = new BaseEntityAttrDescriptor('method', FormControlType.DROPDOWN, 'Method', httpMethodSelectables);
  methodAttr.required = true;

  const pathAttr = new BaseEntityAttrDescriptor('path', FormControlType.TEXT_BOX, 'Path');
  pathAttr.required = true;
  pathAttr.placeholder = "Relative to the tool's base URL, e.g. /rest/api/3/issue";

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  const payloadTemplateAttr = new BaseEntityAttrDescriptor('payloadTemplate', FormControlType.TEXTAREA, 'Payload Template');
  payloadTemplateAttr.styleClass = 'full-width';
  payloadTemplateAttr.placeholder = 'JSON body with ${expression} placeholders';
  payloadTemplateAttr.hideInTable = true;

  // A chip list over what the contract types as `integer[]`. There is no numeric-array control in the
  // workspace, and `TagsComponent` edits strings — so the model holds strings and
  // `ToolDefinitionMapper` converts at the wire boundary. The alternative, an
  // `ADDITIONAL_PROPERTIES` map, would ask the author for keys that do not exist.
  const expectedStatusCodesAttr = new BaseEntityAttrDescriptor('expectedStatusCodes', FormControlType.TAGS, 'Expected Status Codes', undefined, undefined, { inputType: 'number' });
  expectedStatusCodesAttr.placeholder = 'Empty defaults to 200, 201, 204';
  expectedStatusCodesAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, methodAttr, pathAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, expectedStatusCodesAttr, descriptionAttr, payloadTemplateAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createToolOperationDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: TOOL_OPERATION_ENTITY_NAME,
    attrDescriptors: createToolOperationAttrDescriptors(),
    i18nScope: TOOL_OPERATION_I18N_SCOPE,
    componentParent: TOOL_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
