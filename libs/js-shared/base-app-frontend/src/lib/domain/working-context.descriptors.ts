import { AbstractAttrDescriptor, BaseEntityAttrDescriptor, BaseEntityDescriptor, FlexboxDescriptor, FlexDirection, FormControlType, toSelectables } from '@processpuzzle/base-entity';
import { APP_CONTEXT_I18N_SCOPE } from '../base-app.i18n';
import { APP_CONTEXT_ENTITY_NAME, APP_DEFINITION_ENTITY_NAME } from './app-entity-names';

export { APP_CONTEXT_ENTITY_NAME };

/** `WorkingContextDefinition` has no `id`: `name` identifies it, and it is what a route's `requiresContext` names. */
export const APP_CONTEXT_ID_FIELD = 'name';

/** The values of the contract's `persist` enum. */
const PERSIST_MODES = ['url', 'url-local-storage'] as const;

function createContextAttrDescriptors(): AbstractAttrDescriptor[] {
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name', undefined, true);
  nameAttr.required = true;
  nameAttr.isHeading = true;
  nameAttr.placeholder = "The context's URL segment, e.g. race";
  // The contract's own pattern; see the note on the module mount's key.
  nameAttr.pattern = '^[a-z0-9]+(-[a-z0-9]+)*$';

  // A plain text box, as for a route's entity name: whether the entity exists is the backend validator's call.
  const entityNameAttr = new BaseEntityAttrDescriptor('entityName', FormControlType.TEXT_BOX, 'Entity Name');
  entityNameAttr.required = true;
  entityNameAttr.placeholder = 'The entity type the user picks, e.g. Race';

  const parentAttr = new BaseEntityAttrDescriptor('parent', FormControlType.TEXT_BOX, 'Parent');
  parentAttr.placeholder = 'Name of the context this one is nested in';
  parentAttr.hideInTable = true;

  const basePathAttr = new BaseEntityAttrDescriptor('basePath', FormControlType.TEXT_BOX, 'Base Path');
  basePathAttr.placeholder = 'Where its routes are mounted, e.g. /conduct; empty for the app root';
  basePathAttr.hideInTable = true;

  const persistAttr = new BaseEntityAttrDescriptor('persist', FormControlType.DROPDOWN, 'Persist', toSelectables(PERSIST_MODES));
  persistAttr.hideInTable = true;

  // region flattened picker / display / confirmSwitchWhen — re-nested by AppDefinitionMapper
  const pickerSearchFieldsAttr = new BaseEntityAttrDescriptor('pickerSearchFields', FormControlType.TAGS, 'Picker Search Fields');
  pickerSearchFieldsAttr.placeholder = 'Attributes the picker searches; empty for the title attribute';
  pickerSearchFieldsAttr.hideInTable = true;

  const pickerRecentAttr = new BaseEntityAttrDescriptor('pickerRecent', FormControlType.TEXT_BOX, 'Picker Recent', undefined, false, { inputType: 'number' });
  pickerRecentAttr.hideInTable = true;

  const displayLabelAttr = new BaseEntityAttrDescriptor('displayLabel', FormControlType.TEXT_BOX, 'Display Label');
  displayLabelAttr.placeholder = 'Crumb text, e.g. {{name}}; empty for the title attribute';
  displayLabelAttr.hideInTable = true;

  const displayBadgeAttr = new BaseEntityAttrDescriptor('displayBadge', FormControlType.TEXT_BOX, 'Display Badge');
  displayBadgeAttr.placeholder = 'Attribute shown as a badge; state for the state attribute';
  displayBadgeAttr.hideInTable = true;

  const displayExtraAttr = new BaseEntityAttrDescriptor('displayExtra', FormControlType.TEXT_BOX, 'Display Extra');
  displayExtraAttr.placeholder = 'A second text, e.g. round {{currentRound}}';
  displayExtraAttr.hideInTable = true;

  const confirmSwitchStatesAttr = new BaseEntityAttrDescriptor('confirmSwitchStates', FormControlType.TAGS, 'Confirm Switch States');
  confirmSwitchStatesAttr.placeholder = 'States that make leaving the entity ask first, e.g. Running';
  confirmSwitchStatesAttr.hideInTable = true;
  // endregion

  const identityRow = new FlexboxDescriptor([nameAttr, entityNameAttr, parentAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const placementRow = new FlexboxDescriptor([basePathAttr, persistAttr], FlexDirection.ROW);
  placementRow.style = { 'column-gap': '10px' };
  const pickerRow = new FlexboxDescriptor([pickerSearchFieldsAttr, pickerRecentAttr], FlexDirection.ROW);
  pickerRow.style = { 'column-gap': '10px' };
  const displayRow = new FlexboxDescriptor([displayLabelAttr, displayBadgeAttr, displayExtraAttr], FlexDirection.ROW);
  displayRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, placementRow, pickerRow, displayRow, confirmSwitchStatesAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createWorkingContextDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: APP_CONTEXT_ENTITY_NAME,
    attrDescriptors: createContextAttrDescriptors(),
    i18nScope: APP_CONTEXT_I18N_SCOPE,
    componentParent: APP_DEFINITION_ENTITY_NAME,
    isEmbedded: true,
  });
}
