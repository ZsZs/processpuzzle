import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType } from '@processpuzzle/base-entity';
import { CONTENT_BOOK_I18N_SCOPE } from '../base-document.i18n';
import { DOCUMENT_SLUG_PATTERN } from './base-document';
import { CONTENT_BOOK_ENTITY_NAME } from './document-entity-names';

/**
 * The book's properties. The tree is not on the form — it is edited in the book itself and saved with
 * `PUT .../nodes` — which is also why the mapper leaves `nodes` out of every save.
 */
function createContentBookAttrDescriptors(): AbstractAttrDescriptor[] {
  // Same pattern as a document's slug: the contract reuses the `DocumentSlug` schema for both.
  const slugAttr = new BaseEntityAttrDescriptor('slug', FormControlType.TEXT_BOX, 'Slug');
  slugAttr.required = true;
  slugAttr.pattern = DOCUMENT_SLUG_PATTERN;
  slugAttr.placeholder = 'URL key, unique among the books, e.g. handbook';

  const titleAttr = new BaseEntityAttrDescriptor('title', FormControlType.TEXT_BOX, 'Title', undefined, true);
  titleAttr.required = true;
  titleAttr.isHeading = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');

  const isPublicAttr = new BaseEntityAttrDescriptor('isPublic', FormControlType.CHECKBOX, 'Public');
  isPublicAttr.description = 'Anyone may open the book without signing in; reader roles are then ignored';

  const readerRolesAttr = new BaseEntityAttrDescriptor('readerRoles', FormControlType.TAGS, 'Reader roles');
  readerRolesAttr.hideInTable = true;

  const editorRolesAttr = new BaseEntityAttrDescriptor('editorRoles', FormControlType.TAGS, 'Editor roles');
  editorRolesAttr.hideInTable = true;

  const identityRow = new FlexboxDescriptor([slugAttr, titleAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const accessRow = new FlexboxDescriptor([isPublicAttr, readerRolesAttr, editorRolesAttr], FlexDirection.ROW);
  accessRow.style = { 'column-gap': '10px' };

  const container = new FlexboxDescriptor([identityRow, descriptionAttr, accessRow], FlexDirection.COLUMN);
  container.style = { 'row-gap': '5px' };
  return [container];
}

export function createContentBookDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: CONTENT_BOOK_ENTITY_NAME,
    attrDescriptors: createContentBookAttrDescriptors(),
    i18nScope: CONTENT_BOOK_I18N_SCOPE,
  });
}
