import {
  AbstractAttrDescriptor,
  BaseEntityAttrDescriptor,
  BaseEntityDescriptor,
  FlexboxDescriptor,
  FlexDirection,
  FormControlType,
  REALM_ROLE_SUGGESTIONS,
  type Selectable,
  toSelectables,
} from '@processpuzzle/base-entity';
import { CHAT_DEFINITION_I18N_SCOPE } from '../base-chat.i18n';
import { ChatLifecycleKind, ChatMultiplicity, ChatSubjectKind } from './chat-definition';
import { CHAT_DEFINITION_ENTITY_NAME } from './chat-entity-names';

export { CHAT_DEFINITION_ENTITY_NAME };

function tagsAttr(code: string, label: string, roles: boolean): BaseEntityAttrDescriptor {
  const attr = new BaseEntityAttrDescriptor(code, FormControlType.TAGS, label);
  if (roles) attr.tagSuggestions = REALM_ROLE_SUGGESTIONS;
  attr.hideInTable = true;
  return attr;
}

function row(children: AbstractAttrDescriptor[]): FlexboxDescriptor {
  const flexbox = new FlexboxDescriptor(children, FlexDirection.ROW);
  flexbox.style = { 'column-gap': '10px' };
  return flexbox;
}

function createChatDefinitionAttrDescriptors(entityCodes?: () => Selectable[]): AbstractAttrDescriptor[] {
  // Author-chosen and the record's identity: the key is what the conversations of the chat carry.
  const idAttr = new BaseEntityAttrDescriptor('id', FormControlType.TEXT_BOX, 'Key', undefined, true);
  idAttr.required = true;
  idAttr.isHeading = true;
  idAttr.placeholder = 'Lower-case, e.g. order-discussion';
  // The contract's pattern: without it the generated e2e fixture sends prose the backend rejects.
  idAttr.pattern = '^[a-z][a-z0-9-]*$';

  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  const subjectTypeAttr = new BaseEntityAttrDescriptor('subjectType', FormControlType.DROPDOWN, 'Subject Type', toSelectables(Object.keys(ChatSubjectKind)));
  subjectTypeAttr.required = true;

  // A dropdown of the organization's entity types where the facade can supply them, a text box otherwise.
  const subjectDefinitionAttr = entityCodes
    ? new BaseEntityAttrDescriptor('subjectDefinition', FormControlType.DROPDOWN, 'Subject Definition', entityCodes)
    : new BaseEntityAttrDescriptor('subjectDefinition', FormControlType.TEXT_BOX, 'Subject Definition');
  subjectDefinitionAttr.required = true;

  const multiplicityAttr = new BaseEntityAttrDescriptor('multiplicity', FormControlType.DROPDOWN, 'Multiplicity', toSelectables(Object.keys(ChatMultiplicity)));
  multiplicityAttr.required = true;

  const lifecycleAttr = new BaseEntityAttrDescriptor('lifecycle', FormControlType.DROPDOWN, 'Lifecycle', toSelectables(Object.keys(ChatLifecycleKind)));
  lifecycleAttr.required = true;

  // Shown whatever the lifecycle — base-entity has no conditional visibility; the mapper drops it unless STATE_BOUND.
  const writableStatesAttr = tagsAttr('writableStates', 'Writable States', false);
  writableStatesAttr.placeholder = 'STATE_BOUND — the subject states that accept messages';

  const includeSubjectOwnerAttr = new BaseEntityAttrDescriptor('includeSubjectOwner', FormControlType.CHECKBOX, 'Include Subject Owner');
  includeSubjectOwnerAttr.hideInTable = true;

  const retentionDaysAttr = new BaseEntityAttrDescriptor('retentionDays', FormControlType.TEXT_BOX, 'Retention Days', undefined, undefined, { inputType: 'number' });
  retentionDaysAttr.hideInTable = true;

  // Whether the running application has this definition as it stands; changed only by a release, so never edited.
  const releaseStateAttr = new BaseEntityAttrDescriptor('releaseState', FormControlType.TEXT_BOX, 'Release');
  releaseStateAttr.disabled = true;

  const container = new FlexboxDescriptor(
    [
      row([idAttr, nameAttr, releaseStateAttr]),
      row([subjectTypeAttr, subjectDefinitionAttr, multiplicityAttr, lifecycleAttr]),
      writableStatesAttr,
      row([tagsAttr('readRoles', 'Read Roles', true), tagsAttr('writeRoles', 'Write Roles', true), tagsAttr('createRoles', 'Create Roles', true)]),
      row([includeSubjectOwnerAttr, retentionDaysAttr]),
      descriptionAttr,
    ],
    FlexDirection.COLUMN,
  );
  container.style = { 'row-gap': '5px', width: 'fit-content' };
  return [container];
}

/** @param entityCodes the entity types Subject Definition offers */
export function createChatDefinitionDescriptor(entityCodes?: () => Selectable[]): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: CHAT_DEFINITION_ENTITY_NAME,
    attrDescriptors: createChatDefinitionAttrDescriptors(entityCodes),
    i18nScope: CHAT_DEFINITION_I18N_SCOPE,
  });
}
