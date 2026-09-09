import { Selectable } from './base-entity-attr.descriptor';

/**
 * Turns a closed string enum from an API contract (`MATERIAL_THEMES`, `REGION_TYPES`, `PORT_TYPES`,
 * …) into dropdown options. Lives here next to `Selectable` itself rather than in one feature lib,
 * because every metadata-driven feature that has an enum-backed DROPDOWN needs it and none of them
 * depend on each other — the same reason WIDGET_REGISTRY sits in this lib.
 */
export const toSelectables = (values: readonly string[]): Array<Selectable> => values.map((value) => ({ key: value, value }));
