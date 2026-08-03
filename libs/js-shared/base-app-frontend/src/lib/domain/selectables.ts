import { Selectable } from '@processpuzzle/base-entity';

/**
 * Turns one of the contract's closed string enums (`MATERIAL_THEMES`, `REGION_TYPES`, …) into
 * dropdown options. Shared rather than repeated per descriptor file so that `app-definition` and
 * `region-definition` do not have to import each other for it.
 */
export const toSelectables = (values: readonly string[]): Array<Selectable> => values.map((value) => ({ key: value, value }));
