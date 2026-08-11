import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { DOCUMENT_I18N_SCOPE } from '../base-document.i18n';
import { DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './document-entity-names';
import { DOCUMENT_PORT_ID_FIELD } from './document-port.descriptors';

export { DOCUMENT_ENTITY_NAME };

function createDocumentAttrDescriptors(): AbstractAttrDescriptor[] {
  const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title', undefined, true);
  titleAttr.required = true;
  titleAttr.isHeading = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');

  const inputPortsAttr = new BaseEntityAttrDescriptor('inputPorts', FormControlType.EMBEDDED_COMPONENTS, 'Input ports');
  inputPortsAttr.linkedEntityType = DOCUMENT_INPUT_PORT_ENTITY_NAME;
  // A port has no `id` in the contract; `name` is what identifies it. See DOCUMENT_PORT_ID_FIELD.
  inputPortsAttr.referenceIdField = DOCUMENT_PORT_ID_FIELD;
  inputPortsAttr.hideInTable = true;

  const outputPortsAttr = new BaseEntityAttrDescriptor('outputPorts', FormControlType.EMBEDDED_COMPONENTS, 'Output ports');
  outputPortsAttr.linkedEntityType = DOCUMENT_OUTPUT_PORT_ENTITY_NAME;
  outputPortsAttr.referenceIdField = DOCUMENT_PORT_ID_FIELD;
  outputPortsAttr.hideInTable = true;

  // Deliberately no `blocks` attribute here. Content is edited by DocumentEditorComponent
  // through DocumentContentService's block-level endpoints, not through this form — see
  // BaseDocumentContainerComponent for how the two are composed on one screen, and
  // BaseDocumentService.updateProperties/DocumentPropertiesInput for why this form's save
  // is structurally incapable of touching blocks even by accident.
  return [new FlexboxDescriptor([titleAttr, descriptionAttr, inputPortsAttr, outputPortsAttr], FlexDirection.COLUMN)];
}

export function createDocumentDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: DOCUMENT_ENTITY_NAME,
    attrDescriptors: createDocumentAttrDescriptors(),
    i18nScope: DOCUMENT_I18N_SCOPE,
  });
}
