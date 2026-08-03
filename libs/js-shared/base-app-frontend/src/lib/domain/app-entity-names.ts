/**
 * Entity names of the app-definition graph, kept in one dependency-free module.
 *
 * They live here rather than next to their descriptors because the graph is cyclic: `App Definition`
 * aggregates `App Region`, and `App Region` names `App Definition` as its `componentParent`. Each
 * descriptor module re-exports its own name, so importers are unaffected.
 */
export const APP_DEFINITION_ENTITY_NAME = 'App Definition';
export const APP_NAV_ITEM_ENTITY_NAME = 'App Nav Item';
export const APP_PAGE_ENTITY_NAME = 'App Page';
export const APP_REGION_ENTITY_NAME = 'App Region';
export const APP_WIDGET_ENTITY_NAME = 'App Widget';
