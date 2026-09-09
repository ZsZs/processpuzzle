import { describe, expect, it } from 'vitest';
import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, FlexboxDescriptor, FormControlType } from '@processpuzzle/base-entity';
import { DOCUMENT_I18N_SCOPE } from '../base-document.i18n';
import { DOCUMENT_SLUG_PATTERN, DOCUMENT_SOURCE_LOCALES } from './base-document';
import { createDocumentDescriptor, DOCUMENT_ENTITY_NAME } from './base-document.descriptors';
import { DOCUMENT_INPUT_PORT_ENTITY_NAME, DOCUMENT_OUTPUT_PORT_ENTITY_NAME } from './document-entity-names';
import { DOCUMENT_PORT_ID_FIELD } from './document-port.descriptors';

function flattenAttrs(descriptors: AbstractAttrDescriptor[]): BaseEntityAttrDescriptor[] {
  return descriptors.flatMap((descriptor) => (descriptor instanceof FlexboxDescriptor ? flattenAttrs(descriptor.attrDescriptors) : [descriptor as BaseEntityAttrDescriptor]));
}

describe('createDocumentDescriptor', () => {
  const descriptor = createDocumentDescriptor();
  const attrs = flattenAttrs(descriptor.attrDescriptors);
  const byName = (attrName: string) => attrs.find((attr) => attr.attrName === attrName);

  it('names the entity and its translation scope', () => {
    expect(descriptor.entityName).toBe(DOCUMENT_ENTITY_NAME);
    expect(descriptor.i18nKey()).toBe(`${DOCUMENT_I18N_SCOPE}._self`);
  });

  /**
   * All twelve of `DocumentPropertiesInput`, because `PUT .../properties` replaces the properties block: a form
   * short of a field silently blanks it on every save. This is the assertion that fails if the contract grows a
   * field and the form does not.
   */
  it('carries every language-invariant field of DocumentPropertiesInput', () => {
    expect(attrs.map((attr) => attr.attrName)).toEqual([
      'slug',
      'title',
      'sourceLocale',
      'subject',
      'author',
      'description',
      'isPublic',
      'readerRoles',
      'editorRoles',
      'publisherRoles',
      'inputPorts',
      'outputPorts',
    ]);
  });

  /** Content is not among them: it hangs off a translation and is edited through the block endpoints. */
  it('describes no block content', () => {
    expect(byName('blocks')).toBeUndefined();
    expect(byName('translations')).toBeUndefined();
  });

  // Both required by the contract, so the form has to ask for them — a create without either is a 400.
  it('requires the two fields the create endpoint cannot default', () => {
    expect(byName('slug')?.required).toBe(true);
    expect(byName('sourceLocale')?.required).toBe(true);
  });

  /** So a slug the server would reject is refused on the form, in the user's own language. */
  it('validates the slug against the contract’s pattern', () => {
    expect(byName('slug')?.pattern).toBe(DOCUMENT_SLUG_PATTERN);
    expect(new RegExp(DOCUMENT_SLUG_PATTERN).test('getting-started')).toBe(true);
    expect(new RegExp(DOCUMENT_SLUG_PATTERN).test('Getting Started')).toBe(false);
  });

  /** A closed list rather than a patterned text box — `Locale` admits every BCP-47 tag, which is not a choice. */
  it('offers the shipped locales as the source locale, keyed by the tag it stores', () => {
    expect(byName('sourceLocale')?.formControlType).toBe(FormControlType.DROPDOWN);
    expect(byName('sourceLocale')?.getSelectables()).toEqual(DOCUMENT_SOURCE_LOCALES.map((locale) => ({ key: locale, value: locale })));
  });

  /** Plain role names by contract, with no registry to pick from, so a chip list is the honest control. */
  it.each(['readerRoles', 'editorRoles', 'publisherRoles'])('edits %s as a chip list', (attrName) => {
    expect(byName(attrName)?.formControlType).toBe(FormControlType.TAGS);
  });

  it('marks the title as the row identification and the form heading', () => {
    expect(byName('title')?.isLinkToDetails).toBe(true);
    expect(byName('title')?.isHeading).toBe(true);
    expect(byName('title')?.required).toBe(true);
  });

  /** A port has no `id` in the contract; `name` identifies a row, so the list has to be told that. */
  it.each([
    ['inputPorts', DOCUMENT_INPUT_PORT_ENTITY_NAME],
    ['outputPorts', DOCUMENT_OUTPUT_PORT_ENTITY_NAME],
  ])('carries %s as an embedded list keyed by name', (attrName, linkedEntityType) => {
    expect(byName(attrName)?.formControlType).toBe(FormControlType.EMBEDDED_COMPONENTS);
    expect(byName(attrName)?.linkedEntityType).toBe(linkedEntityType);
    expect(byName(attrName)?.referenceIdField).toBe(DOCUMENT_PORT_ID_FIELD);
  });

  /** The list shows what identifies a document, not its whole metadata block. */
  it('keeps the secondary metadata out of the table', () => {
    expect(['subject', 'author', 'readerRoles', 'editorRoles', 'publisherRoles', 'inputPorts', 'outputPorts'].every((attrName) => byName(attrName)?.hideInTable === true)).toBe(true);
    expect(byName('slug')?.hideInTable).toBe(false);
    expect(byName('title')?.hideInTable).toBe(false);
  });
});
