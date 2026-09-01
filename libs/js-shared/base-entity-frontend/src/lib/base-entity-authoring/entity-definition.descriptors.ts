import { FormControlType, type AbstractAttrDescriptor } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { FlexboxDescriptor, FlexDirection } from '../base-entity/flexboxDescriptor';
import { toSelectables } from '../base-entity/selectables';
import { ENTITY_DEFINITION_STATUSES } from '../base-entity-definition/entity-definition';
import { ENTITY_DEFINITION_I18N_SCOPE } from '../i18n/base-entity.i18n';
import { ENTITY_ATTRIBUTE_ID_FIELD } from './entity-attribute.descriptors';
import { ENTITY_ATTRIBUTE_ENTITY_NAME, ENTITY_DEFINITION_ENTITY_NAME } from './entity-authoring-names';

export { ENTITY_DEFINITION_ENTITY_NAME };

function createEntityDefinitionAttrDescriptors(): AbstractAttrDescriptor[] {
  // The business key *and* the record's identity — see `EntityDefinition.id`. `isLinkToDetails`, so the
  // list's own column is what opens the definition, and `isHeading`, so the form and the status bar name
  // the entity type being authored.
  const codeAttr = new BaseEntityAttrDescriptor('code', FormControlType.TEXT_BOX, 'Code', undefined, true);
  codeAttr.required = true;
  codeAttr.isHeading = true;
  codeAttr.placeholder = 'URL-safe key of the entity type, e.g. order';

  // What the rest of the platform calls the *entity name*: a `BaseEntityDescriptor`, an `AppDefinition`
  // route and `BASE_ENTITY_FACADE_REGISTRY` are all keyed by this, while the definition itself is keyed by
  // `code`. `EntityDefinitionRegistry` is the one place that knows the two are different things.
  const nameAttr = new BaseEntityAttrDescriptor('name', FormControlType.TEXT_BOX, 'Name');
  nameAttr.required = true;
  nameAttr.placeholder = 'Entity name the screens are keyed by, e.g. Order';

  const descriptionAttr = new BaseEntityAttrDescriptor('description', FormControlType.TEXTAREA, 'Description');
  descriptionAttr.styleClass = 'full-width';
  descriptionAttr.hideInTable = true;

  // Only an `ACTIVE` definition is rendered (`isRenderable`), so the column belongs in the table: which
  // types a tenant's application actually shows is the first thing this list has to answer.
  const statusAttr = new BaseEntityAttrDescriptor('status', FormControlType.DROPDOWN, 'Status', toSelectables(ENTITY_DEFINITION_STATUSES));

  // region containment
  // `isEmbedded` without a `componentParents` entry is a 422 from the backend — an embedded definition has
  // to name the type whose payload carries it. Both are shown side by side so the pair reads as one
  // decision, which is also how `BaseEntityDescriptor` treats them.
  const isEmbeddedAttr = new BaseEntityAttrDescriptor('isEmbedded', FormControlType.CHECKBOX, 'Embedded');

  // A TAGS control, not a dropdown over the tenant's definitions: the parents are named by **code**, and
  // the codes on offer are rows of the very list this form was opened from — a closed option list would be
  // stale as soon as one is added. `descriptorOf` drops a code with no definition behind it, and the
  // backend rejects one on save.
  const componentParentsAttr = new BaseEntityAttrDescriptor('componentParents', FormControlType.TAGS, 'Component Parents');
  componentParentsAttr.hideInTable = true;
  // endregion

  // The attributes of the type, contained rather than associated: the contract nests them inside the
  // definition document, `PUT /entity-definitions/{code}` replaces the whole list, and there is no
  // endpoint that lists an attribute on its own. So the rows travel inside this entity's payload and are
  // saved with it — which is what makes `EMBEDDED_COMPONENTS` the right control and
  // `EntityAttributeFacade` an `EmbeddedEntityFacade`.
  const attributesAttr = new BaseEntityAttrDescriptor('attributes', FormControlType.EMBEDDED_COMPONENTS, 'Attributes');
  attributesAttr.linkedEntityType = ENTITY_ATTRIBUTE_ENTITY_NAME;
  attributesAttr.referenceIdField = ENTITY_ATTRIBUTE_ID_FIELD;
  attributesAttr.hideInTable = true;

  // region server-assigned
  // Shown so the author can see which revision is on screen, never edited here. `version` is the
  // optimistic lock the backend holds; `BaseEntityDefinitionInput` has no field to send it back in, so
  // this is a read-out and not a concurrency guard the client could win.
  const versionAttr = new BaseEntityAttrDescriptor('version', FormControlType.TEXT_BOX, 'Version');
  versionAttr.disabled = true;

  const updatedAtAttr = new BaseEntityAttrDescriptor('updatedAt', FormControlType.TEXT_BOX, 'Updated At');
  updatedAtAttr.disabled = true;
  // endregion

  const identityRow = new FlexboxDescriptor([codeAttr, nameAttr, statusAttr], FlexDirection.ROW);
  identityRow.style = { 'column-gap': '10px' };
  const containmentRow = new FlexboxDescriptor([isEmbeddedAttr, componentParentsAttr], FlexDirection.ROW);
  containmentRow.style = { 'column-gap': '10px' };
  const revisionRow = new FlexboxDescriptor([versionAttr, updatedAtAttr], FlexDirection.ROW);
  revisionRow.style = { 'column-gap': '10px' };

  const flexBoxContainer = new FlexboxDescriptor([identityRow, containmentRow, revisionRow, descriptionAttr, attributesAttr], FlexDirection.COLUMN);
  flexBoxContainer.style = { 'row-gap': '5px', width: 'fit-content' };
  return [flexBoxContainer];
}

export function createEntityDefinitionDescriptor(): BaseEntityDescriptor {
  return new BaseEntityDescriptor({
    entityName: ENTITY_DEFINITION_ENTITY_NAME,
    attrDescriptors: createEntityDefinitionAttrDescriptors(),
    i18nScope: ENTITY_DEFINITION_I18N_SCOPE,
  });
}
