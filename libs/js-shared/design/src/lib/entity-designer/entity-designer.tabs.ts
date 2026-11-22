/** One tab of the Entities section: the child route it opens, its icon and its `design` scope label. */
export interface EntityDesignerTab {
  /** Path of the child route, relative to `entities`. */
  path: string;
  icon: string;
  /** Key in the `design` transloco scope — the scope the `/design` branch registers. */
  label: string;
}

/**
 * The tabs of the Entities section, in the order they are shown: the entity types the author came for, then the units
 * the organization adds to the standard catalog — what a QUANTITY attribute offers — and the exchange rates it enters, which
 * money converts at.
 *
 * `entity-definition`, `organization-unit` and `exchange-rate` are the segments of `BASE_ENTITY_AUTHORING_ROUTES`, each
 * `snakeCaseName(entityName)`, which `BaseFormNavigatorSingletonStore` builds the details URL from. A list of its own rather
 * than derived from the children's `menuTitle`; `design.routes.spec.ts` keeps the two in step. Separate from
 * `design.routes.ts` to avoid the import cycle.
 */
export const ENTITY_DESIGNER_TABS: EntityDesignerTab[] = [
  { path: 'entity-definition', icon: 'checkbook', label: 'design.entity-definitions' },
  { path: 'organization-unit', icon: 'straighten', label: 'design.units' },
  { path: 'exchange-rate', icon: 'currency_exchange', label: 'design.exchange-rates' },
];
