import { BaseEntityAttrDescriptor, FormControlType, type SelectablesInput } from '@processpuzzle/base-entity';

/**
 * A `BaseEntityAttrDescriptor` that renders but cannot be edited.
 *
 * The whole execution layer is read-only — `base-workflow-api.yaml` defines no `PUT` for an instance,
 * a task instance or a work product instance — and base-entity has no "read-only entity" flag, so
 * read-only is assembled from the two levers it does have:
 *
 *  - `disabled` on every attribute, which is what this helper sets. `BaseEntityFormBuilder` passes it
 *    straight to `new FormControl({ value, disabled })`, so the control is greyed out and, because a
 *    disabled control never becomes dirty, `saveDisabled()` also stays true.
 *  - `isAbstract` on the descriptor, set by each factory in this folder. That disables New, Edit and
 *    Delete in the toolbar and Save and Delete on the form.
 *
 * Neither *hides* an action — base-entity's form template has no `@if` around its buttons — so the
 * buttons are visible and inert. That is the honest rendering of a resource whose only mutations are
 * verbs (`/assign`, `/complete`, `/skip`) that no generated form knows how to invoke; when this
 * library grows an action surface for them, it belongs in `extraFormActionsTemplate`, next to the
 * inert Save rather than instead of it.
 */
export function readOnlyAttr(attrName: string, formControlType: FormControlType, label: string, selectables?: SelectablesInput, isLinkToDetails?: boolean): BaseEntityAttrDescriptor {
  const attrDescriptor = new BaseEntityAttrDescriptor(attrName, formControlType, label, selectables, isLinkToDetails);
  attrDescriptor.disabled = true;
  return attrDescriptor;
}
