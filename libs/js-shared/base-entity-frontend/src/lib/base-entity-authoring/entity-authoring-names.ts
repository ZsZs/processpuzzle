/**
 * Entity names of the definition graph, kept in one dependency-free module.
 *
 * They live here rather than next to their descriptors because the graph is cyclic: the
 * `Entity Definition` descriptor aggregates `Entity Attribute`, and that one names the definition back as
 * its `componentParent`. Each descriptor module re-exports its own name, so importers are unaffected.
 * Same arrangement as base-state's `state-entity-names.ts` and base-app's `app-entity-names.ts`.
 *
 * `BASE_ENTITY_FACADE_REGISTRY` is one flat map for the whole application, so a name has to be specific
 * enough that no other feature would plausibly claim it. These two also have a second reason to be
 * distinctive: a *tenant* authors entity names through these very screens, and a definition named
 * `Entity Definition` would then shadow the screens that created it.
 */
export const ENTITY_DEFINITION_ENTITY_NAME = 'Entity Definition';
export const ENTITY_ATTRIBUTE_ENTITY_NAME = 'Entity Attribute';

/**
 * The units an organization adds to the standard catalog — a branch of its own beside the definition graph, in the same
 * transloco scope. Distinctive for the same reason as the two above: the registry is one flat map for the application.
 */
export const ORGANIZATION_UNIT_ENTITY_NAME = 'Organization Unit';

/** The exchange rates an organization enters — the third routable entity of the authoring branch, in the same transloco scope. */
export const EXCHANGE_RATE_ENTITY_NAME = 'Exchange Rate';
