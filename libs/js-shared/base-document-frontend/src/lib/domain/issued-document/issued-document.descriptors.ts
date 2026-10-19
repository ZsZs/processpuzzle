import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, DEFAULT_DATE_TIME_FORMAT, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { ISSUED_DOCUMENT_I18N_SCOPE } from '../../base-document.i18n';
import { ISSUED_DOCUMENT_ENTITY_NAME } from './issued-document';

function createIssuedDocumentAttrDescriptors(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.isHeading = true;

  const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Document');
  const subjectTypeAttr = new BaseEntityAttrDescriptor('subjectType', FormControlType.TEXT_BOX, 'Subject type');
  const subjectIdAttr = new BaseEntityAttrDescriptor('subjectId', FormControlType.TEXT_BOX, 'Subject');
  const signedByAttr = new BaseEntityAttrDescriptor('signedBy', FormControlType.TEXT_BOX, 'Signed by');
  const signedAtAttr = new BaseEntityAttrDescriptor('signedAt', FormControlType.DATE, 'Signed at');
  signedAtAttr.dateFormat = { ...DEFAULT_DATE_TIME_FORMAT };
  const pdfAttr = new BaseEntityAttrDescriptor('pdf', FormControlType.ARTIFACT, 'PDF');

  // Everything is the server's, so the form only shows.
  for (const attr of [nameAttr, titleAttr, subjectTypeAttr, subjectIdAttr, signedByAttr, signedAtAttr, pdfAttr]) attr.disabled = true;

  const row = new FlexboxDescriptor([titleAttr, subjectTypeAttr, subjectIdAttr], FlexDirection.ROW);
  row.style = { 'column-gap': '10px' };
  const signatureRow = new FlexboxDescriptor([signedByAttr, signedAtAttr], FlexDirection.ROW);
  signatureRow.style = { 'column-gap': '10px' };
  const container = new FlexboxDescriptor([nameAttr, row, signatureRow, pdfAttr], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px' };
  return [container];
}

export function createIssuedDocumentDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ISSUED_DOCUMENT_ENTITY_NAME,
    attrDescriptors: createIssuedDocumentAttrDescriptors(),
    i18nScope: ISSUED_DOCUMENT_I18N_SCOPE,
    isReadOnly: true,
  });
}
