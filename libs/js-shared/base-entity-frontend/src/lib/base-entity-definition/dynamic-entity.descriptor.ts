import { FormControlType } from '../base-entity/abstact-attr.descriptor';
import { BaseEntityAttrDescriptor } from '../base-entity/base-entity-attr.descriptor';
import { BaseEntityDescriptor } from '../base-entity/base-entity.descriptor';
import { toSelectables } from '../base-entity/selectables';
import { EntityAttributeDefinition, EntityDefinition } from './entity-definition';

/**
 * Resolves a definition by its `code` — how an attribute names an embedded child or a foreign-key target.
 * Supplied by the caller rather than injected, so this module stays a pure function of its inputs.
 */
export type DefinitionLookup = (code: string) => EntityDefinition | undefined;

/**
 * The contract's `FormControlType` is a **superset** of the frontend's: it names value-shaped kinds
 * (`TEXT`, `NUMBER`, `BOOLEAN`, `DATE_TIME`, `ENUM_SELECT`) that the frontend expresses through the
 * control it renders them with. This table is that translation and nothing else — every value on the
 * right is a key of `FORM_CONTROL_COMPONENTS` in `base-entity-form.builder.ts`, which is what makes an
 * unmapped one a runtime throw rather than a blank field.
 *
 * The pairs worth justifying:
 * - `NUMBER` also sets `inputType: 'number'` (see {@link optionsOf}) — a textbox is the right control,
 *   the keyboard and the spinner are what differ.
 * - `DATE_TIME` maps to `DATE`: `DatepickerComponent` is the only temporal control there is. The time of
 *   day survives the round trip untouched because the payload is stored as the string the backend sent;
 *   it is simply not editable yet.
 * - `BOOLEAN` and `CHECKBOX` both map to `CHECKBOX` — the contract distinguishes the value kind from the
 *   widget, the frontend has one widget for it.
 * - `ENUM_SELECT` and `DROPDOWN` both map to `DROPDOWN`, fed by `enumValues`.
 */
const CONTROL_TYPES: Readonly<Record<string, FormControlType>> = {
  ADDITIONAL_PROPERTIES: FormControlType.ADDITIONAL_PROPERTIES,
  ARTIFACT: FormControlType.ARTIFACT,
  BOOLEAN: FormControlType.CHECKBOX,
  CHECKBOX: FormControlType.CHECKBOX,
  COMPONENTS: FormControlType.COMPONENTS,
  DATE: FormControlType.DATE,
  DATE_TIME: FormControlType.DATE,
  DROPDOWN: FormControlType.DROPDOWN,
  EMBEDDED_COMPONENTS: FormControlType.EMBEDDED_COMPONENTS,
  ENUM_SELECT: FormControlType.DROPDOWN,
  FLEX_BOX: FormControlType.FLEX_BOX,
  FOREIGN_KEY: FormControlType.FOREIGN_KEY,
  LABEL: FormControlType.LABEL,
  LOOKUP: FormControlType.LOOKUP,
  NUMBER: FormControlType.TEXT_BOX,
  RADIO: FormControlType.RADIO,
  RELATED_ENTITIES: FormControlType.RELATED_ENTITIES,
  TAGS: FormControlType.TAGS,
  TEXT: FormControlType.TEXT_BOX,
  TEXTAREA: FormControlType.TEXTAREA,
  TEXT_BOX: FormControlType.TEXT_BOX,
  TITLE: FormControlType.TITLE,
};

/**
 * A control type the frontend can render.
 *
 * An unrecognized value falls back to `TEXT_BOX` rather than throwing. A definition may name a control the
 * running frontend has not caught up to — the same situation an `AppDefinition` naming an unknown route
 * kind is in, and treated the same way: degrade at the one field concerned instead of taking a screen
 * down. A text box shows the value and lets it be edited, which is the least wrong thing to do with an
 * attribute nobody has a widget for.
 */
export function controlTypeOf(attribute: EntityAttributeDefinition): FormControlType {
  return CONTROL_TYPES[attribute.formControlType] ?? FormControlType.TEXT_BOX;
}

/**
 * Turns one entity definition into the descriptor the generated screens render from.
 *
 * The result is an ordinary {@link BaseEntityDescriptor} — the same object a hand-written
 * `createDescriptor()` returns — which is the whole point: nothing downstream of here can tell a
 * metadata-defined entity from a compiled one. `store` is left unset; the facade binds it (see
 * `BaseEntityFacade.descriptor`).
 *
 * Three translations happen here, all of them from the contract's vocabulary to the descriptor's:
 * - **code → name.** `componentParents` and every `linkedEntityType` name a definition by `code`, while
 *   the descriptor's equivalents name an entity by `entityName`. A code with no definition behind it is
 *   dropped rather than passed through: `embeddedAttrFor()` and `isComponentOf()` compare against names,
 *   so a stray code would silently match nothing anyway, and dropping it keeps a broken reference from
 *   also breaking `isEmbedded`'s "must name a parent" invariant.
 * - **`enumValues` → `selectables`.** Through `toSelectables`, so the options are keyed exactly as a
 *   hand-written descriptor's are.
 * - **child `isLinkToDetails` → `referenceIdField`.** See {@link referenceIdFieldOf}.
 *
 * Attributes are ordered by `displayOrder`, which is the authored field order and therefore the form's
 * and the table's column order. Ties keep the order the backend sent, so a definition that sets no
 * `displayOrder` at all still renders in its declared order.
 */
export function descriptorOf(definition: EntityDefinition, lookup: DefinitionLookup): BaseEntityDescriptor {
  const attributes = [...(definition.attributes ?? [])].sort(byDisplayOrder);
  const componentParents = namesOf(definition.componentParents, lookup);

  return new BaseEntityDescriptor({
    entityName: definition.name,
    entityTitle: definition.name,
    attrDescriptors: attributes.map((attribute) => attrDescriptorOf(attribute, lookup)),
    componentParent: componentParents,
    // `isEmbedded` is dropped when no parent name resolved, because the descriptor's constructor throws on
    // that combination — rightly, since such a component has neither an endpoint of its own nor a payload
    // to travel in. A definition in that state is malformed (or names a parent this tenant has deleted),
    // and it is worth more to render its attributes read-through than to fail the whole route with an
    // exception thrown while the router is building children.
    isEmbedded: definition.isEmbedded === true && componentParents.length > 0,
  });
}

/** One attribute as an `BaseEntityAttrDescriptor`, with everything the definition has to say about it. */
function attrDescriptorOf(attribute: EntityAttributeDefinition, lookup: DefinitionLookup): BaseEntityAttrDescriptor {
  const attrDescriptor = new BaseEntityAttrDescriptor(
    attribute.code,
    controlTypeOf(attribute),
    // The authored `name` is the label, falling back to the code. It is also the *fallback* for the
    // transloco key the descriptor derives (`<entity>.<attr>`), so a tenant that seeds a bundle gets a
    // translated label and one that does not gets the authored one — never a bare attribute code.
    attribute.name ?? attribute.code,
    attribute.enumValues?.length ? toSelectables(attribute.enumValues) : undefined,
    attribute.isLinkToDetails === true,
    optionsOf(attribute),
  );

  attrDescriptor.required = attribute.required === true;
  attrDescriptor.description = attribute.description;

  const linkedEntityName = attribute.linkedEntityType ? lookup(attribute.linkedEntityType)?.name : undefined;
  if (linkedEntityName) attrDescriptor.linkedEntityType = linkedEntityName;

  if (attrDescriptor.formControlType === FormControlType.EMBEDDED_COMPONENTS) {
    attrDescriptor.referenceIdField = referenceIdFieldOf(attribute, lookup);
    // A to-many list of sub-forms is not a table cell. Every hand-written descriptor in this workspace
    // hides its embedded lists from the table for the same reason.
    attrDescriptor.hideInTable = true;
  }

  return attrDescriptor;
}

/**
 * What identifies one embedded row in a URL segment.
 *
 * An embedded row has no `id` — it is addressed by its position in the owner's payload, and the URL
 * carries a *key* that `indexOfRow` looks that position up by. The contract has no `referenceIdField`, so
 * it is derived: the child definition's `isLinkToDetails` attribute is by construction the one that
 * titles a row, and therefore the one a user would recognize in a URL.
 *
 * **Not cosmetic.** With no `referenceIdField`, `rowId()` reads `row['id']`, which an embedded row does
 * not have, and returns `''` — whereupon `indexOfRow` answers `-1` for every row and no embedded form can
 * be opened at all. So the fallback for a child that declares no title attribute matters, and it depends
 * on what kind of child it is:
 * - **embedded** — its leading attribute, the first by `displayOrder`. Authoring the flag is one
 *   checkbox, and forgetting it would otherwise make every row of that definition silently unopenable:
 *   the list renders, the rows are there, clicking one does nothing. The leading field is what the row's
 *   list entry is labelled with anyway, so it is also the segment a user would expect in the URL.
 * - **not embedded** — `'id'`, the default, which is right for a child that does carry ids.
 *
 * A heuristic either way, and the same one twice over: uniqueness among sibling rows is the author's to
 * guarantee, exactly as it already is for an `isLinkToDetails` attribute.
 */
export function referenceIdFieldOf(attribute: EntityAttributeDefinition, lookup: DefinitionLookup): string {
  const child = attribute.linkedEntityType ? lookup(attribute.linkedEntityType) : undefined;
  const attributes = [...(child?.attributes ?? [])].sort(byDisplayOrder);
  const titleAttribute = attributes.find((candidate) => candidate.isLinkToDetails === true);

  return titleAttribute?.code ?? (child?.isEmbedded === true ? attributes[0]?.code : undefined) ?? 'id';
}

/**
 * The `options` bag `BaseEntityAttrDescriptor` passes to its control. Only the input type is decided from
 * metadata today, and only for `NUMBER` — `TextboxComponent` binds it to `<input [type]>`, so this is
 * what makes a numeric attribute get a numeric keyboard and spinner instead of a plain text field.
 */
function optionsOf(attribute: EntityAttributeDefinition): { inputType: 'text' | 'number' } | undefined {
  return attribute.formControlType === 'NUMBER' || attribute.valueKind === 'NUMBER' ? { inputType: 'number' } : undefined;
}

/** Definition codes as entity names, dropping any code no definition answers to. */
function namesOf(codes: string[] | undefined, lookup: DefinitionLookup): string[] {
  return (codes ?? []).map((code) => lookup(code)?.name).filter((name): name is string => !!name);
}

/** Ascending `displayOrder`; an attribute that declares none sorts after the ones that do, in arrival order. */
function byDisplayOrder(left: EntityAttributeDefinition, right: EntityAttributeDefinition): number {
  return (left.displayOrder ?? Number.MAX_SAFE_INTEGER) - (right.displayOrder ?? Number.MAX_SAFE_INTEGER);
}
