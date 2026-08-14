/**
 * Entity names of the app-definition graph, kept in one dependency-free module.
 *
 * They live here rather than next to their descriptors because the graph is cyclic: `App Definition`
 * aggregates `App Region`, and `App Region` names `App Definition` as its `componentParent`. Each
 * descriptor module re-exports its own name, so importers are unaffected.
 */
export const APP_DEFINITION_ENTITY_NAME = 'App Definition';
/**
 * A second aggregate root beside `App Definition`, not a part of it — hence no `App` prefix, which in
 * every other name here marks something the app document carries. `App Module Mount` is the row inside
 * an app that *names* one of these.
 */
export const MODULE_DEFINITION_ENTITY_NAME = 'Module Definition';
export const APP_MODULE_MOUNT_ENTITY_NAME = 'App Module Mount';
export const APP_NAV_ITEM_ENTITY_NAME = 'App Nav Item';
export const APP_REGION_ENTITY_NAME = 'App Region';
export const APP_ROUTE_ENTITY_NAME = 'App Route';
export const APP_WIDGET_ENTITY_NAME = 'App Widget';
