import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { DOCUMENT_I18N_SCOPE } from '../base-document.i18n';
import { DOCUMENT_SLUG_PATTERN, DOCUMENT_SOURCE_LOCALES } from './base-document';
import { DOCUMENT_ENTITY_NAME, DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './document-entity-names';
import { DOCUMENT_PORT_ID_FIELD } from './document-port.descriptors';

export { DOCUMENT_ENTITY_NAME };

const sourceLocaleSelectables = toSelectables([...DOCUMENT_SOURCE_LOCALES]);

function createDocumentAttrDescriptors(): AbstractAttrDescriptor[] {
  // Required and pattern-validated because the contract makes it both: `POST /documents` and
  // `PUT .../properties` reject a body without a slug, and one whose slug is not `[a-z0-9-]` — so the form
  // says so where it was typed rather than surfacing the server's validation message.
  const slugAttr = new BaseEntityAttrDescriptor('slug', FormControlType.TEXT_BOX, 'Slug');
  slugAttr.required = true;
  slugAttr.pattern = DOCUMENT_SLUG_PATTERN;
  slugAttr.placeholder = 'URL key, unique in the organization, e.g. getting-started';

  const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title', undefined, true);
  titleAttr.required = true;
  titleAttr.isHeading = true;

  const subjectAttr = new BaseEntityAttrDescriptor('subject', FormControlType.TEXT_BOX, 'Subject');
  subjectAttr.placeholder = 'What the document is about, one line';
  subjectAttr.hideInTable = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');

  const authorAttr = new BaseEntityAttrDescriptor('author', FormControlType.TEXT_BOX, 'Author');
  authorAttr.placeholder = 'Byline; defaults to whoever created the document';
  authorAttr.hideInTable = true;

  // A closed list rather than a patterned text box: `Locale` admits every BCP-47 tag, and the ones an
  // application has translations for are what a source locale can honestly be. See DOCUMENT_SOURCE_LOCALES.
  const sourceLocaleAttr = new BaseEntityAttrDescriptor('sourceLocale', FormControlType.DROPDOWN, 'Source locale', sourceLocaleSelectables);
  sourceLocaleAttr.required = true;

  const isPublicAttr = new BaseEntityAttrDescriptor('isPublic', FormControlType.CHECKBOX, 'Public');
  isPublicAttr.description = 'Published content is readable without authentication; reader roles are then ignored';

  // Plain role names by contract, with no referential link to a role registry — so a chip list, not a picker
  // over some list this form would have to invent. Empty means "any authenticated member".
  const readerRolesAttr = new BaseEntityAttrDescriptor('readerRoles', FormControlType.TAGS, 'Reader roles');
  readerRolesAttr.hideInTable = true;

  const editorRolesAttr = new BaseEntityAttrDescriptor('editorRoles', FormControlType.TAGS, 'Editor roles');
  editorRolesAttr.hideInTable = true;

  const publisherRolesAttr = new BaseEntityAttrDescriptor('publisherRoles', FormControlType.TAGS, 'Publisher roles');
  publisherRolesAttr.hideInTable = true;

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
  const identityRow = new FlexboxDescriptor([slugAttr, titleAttr, sourceLocaleAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const aboutRow = new FlexboxDescriptor([subjectAttr, authorAttr], FlexDirection.ROW);
  aboutRow.style = { 'column-gap': '10px' };
  const accessRow = new FlexboxDescriptor([isPublicAttr, readerRolesAttr, editorRolesAttr, publisherRolesAttr], FlexDirection.ROW);
  accessRow.style = { 'column-gap': '10px' };
  const portsRow = new FlexboxDescriptor([inputPortsAttr, outputPortsAttr], FlexDirection.ROW);
  portsRow.style = { 'column-gap': '10px' };

  const container = new FlexboxDescriptor([identityRow, aboutRow, descriptionAttr, accessRow, portsRow], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px' };
  return [container];
}

export function createDocumentDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: DOCUMENT_ENTITY_NAME,
    attrDescriptors: createDocumentAttrDescriptors(),
    i18nScope: DOCUMENT_I18N_SCOPE,
  });
}
