import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { TOOL_DEFINITION_I18N_SCOPE } from '../../base-workflow.i18n';
import { AuthType } from './tool-definition';
import { TOOL_DEFINITION_ENTITY_NAME, TOOL_OPERATION_ENTITY_NAME } from '../workflow-entity-names';
import { TOOL_OPERATION_ID_FIELD } from './tool-operation.descriptors';

export { TOOL_DEFINITION_ENTITY_NAME };

const authTypeSelectables = toSelectables(Object.keys(AuthType));

function createToolDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Id', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Unique per organization, e.g. jira';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const baseUrlAttr = new BaseEntityAttrDescriptor('baseUrl', FormControlType.TEXT_BOX, 'Base URL');
  baseUrlAttr.required = true;
  baseUrlAttr.placeholder = 'https://example.atlassian.net';
  // `format: uri` in the contract, which the generator turns into a `java.net.URI` field — so a value with
  // whitespace in it is rejected at deserialization, before any validator runs, and the caller sees a bare
  // 400. This is the narrowest honest form of that constraint, and declaring it here is also what makes the
  // generated e2e fixture emit a dashed token rather than prose (`control-tester`'s `patternedValue` only
  // does so for an attribute with a pattern).
  baseUrlAttr.pattern = '^\\S+$';

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // region auth — flattened out of `auth` by ToolDefinitionMapper
  // The contract nests these two inside an `auth` object, which no descriptor can reach: an attribute
  // names one property, not a path. So they are authored here as siblings and re-nested on save, the
  // way base-app authors `AppDefinition.theme`.
  const authTypeAttr = new BaseEntityAttrDescriptor('type', FormControlType.DROPDOWN, 'Auth Type', authTypeSelectables);

  const secretRefAttr = new BaseEntityAttrDescriptor('secretRef', FormControlType.TEXT_BOX, 'Secret Reference');
  secretRefAttr.placeholder = 'Env var name or Vault path — never the credential itself';
  secretRefAttr.hideInTable = true;
  // endregion

  // Server-assigned: shown so the author can see which revision is on screen, never edited here.
  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const createdAtAttr = new BaseEntityAttrDescriptor('createdAt', FormControlType.TEXT_BOX, 'Created At');
  createdAtAttr.disabled = true;

  // Containment: the contract nests the operations inside the tool document and gives them no
  // endpoint of their own, so they travel inside this entity's payload and are saved with it.
  const operationsAttr = new BaseEntityAttrDescriptor('operations', FormControlType.EMBEDDED_COMPONENTS, 'Operations');
  operationsAttr.linkedEntityType = TOOL_OPERATION_ENTITY_NAME;
  operationsAttr.referenceIdField = TOOL_OPERATION_ID_FIELD;
  operationsAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([idAttr, nameAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const endpointRow = new FlexboxDescriptor([baseUrlAttr, authTypeAttr, secretRefAttr], FlexDirection.ROW);
  endpointRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([versionAttr, createdAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, endpointRow, revisionRow, descriptionAttr, operationsAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createToolDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: TOOL_DEFINITION_ENTITY_NAME,
    attrDescriptors: createToolDefinitionAttrDescriptors(),
    i18nScope: TOOL_DEFINITION_I18N_SCOPE,
  });
}
