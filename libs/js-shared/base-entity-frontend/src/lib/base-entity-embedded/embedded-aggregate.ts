import { BaseEntity, PersistedEntity } from '../base-entity/base-entity';

/**
 * One hop down an aggregate: the `EMBEDDED_COMPONENTS` attribute followed, and which of its rows.
 * A path is the list of hops from the aggregate root, so `[]` addresses the root itself.
 */
export interface EmbeddedPathStep {
  attrName: string;
  index: number;
}

export type EmbeddedPath = readonly EmbeddedPathStep[];

/**
 * A row as it sits in the payload. Not `BaseEntity`: an embedded row is not guaranteed to carry an `id` —
 * `App Region` has none and is identified by `type` (see `referenceIdField`) — and `BaseEntity`'s only
 * property is an optional `id`, so TypeScript's weak-type rule rejects exactly those rows.
 */
export type EmbeddedRow = BaseEntity | Record<string, unknown>;

type Payload = Record<string, unknown>;

/**
 * An embedded child has no table of its own, so it is addressed by its **position** inside the root's
 * payload — `regions[0].navItems[2]` — rather than by a globally unique id. These functions are the only
 * place that knows how to read and write such a position; everything above them (the service, the list
 * control, the route context) works in terms of a path.
 *
 * They are deliberately free of Angular and of the store: an aggregate is a plain payload, and keeping the
 * arithmetic pure is what makes the nested and id-less cases cheap to test.
 */

/** Resolves the object a path points at, or `undefined` when any hop is missing. */
export function readOwner(root: unknown, path: EmbeddedPath): Payload | undefined {
  let owner = asPayload(root);

  for (const step of path) {
    const rows = owner ? asArray(owner[step.attrName]) : undefined;
    owner = asPayload(rows?.[step.index]);
    if (!owner) return undefined;
  }

  return owner;
}

/** The rows of one `EMBEDDED_COMPONENTS` attribute; an empty array when the attribute is unset. */
export function readRows(root: unknown, path: EmbeddedPath, attrName: string): EmbeddedRow[] {
  const owner = readOwner(root, path);
  return asArray(owner?.[attrName])?.map((row) => asPayload(row) ?? {}) ?? [];
}

/**
 * A copy of `root` whose owner carries `rows` under `attrName`, sharing every untouched branch. The
 * aggregate is persisted by replacing the root document wholesale, so a write has to rebuild only the spine
 * from the root down to the owner.
 */
export function writeRows(root: unknown, path: EmbeddedPath, attrName: string, rows: readonly EmbeddedRow[]): Payload {
  const owner = asPayload(root) ?? {};

  if (path.length === 0) {
    return { ...owner, [attrName]: [...rows] };
  }

  const [step, ...remainingPath] = path;
  const siblings = asArray(owner[step.attrName]) ?? [];
  if (step.index < 0 || step.index >= siblings.length) {
    throw new Error(`Cannot write '${attrName}': '${step.attrName}[${step.index}]' does not exist in the aggregate.`);
  }

  const updatedSiblings = [...siblings];
  updatedSiblings[step.index] = writeRows(siblings[step.index], remainingPath, attrName, rows);
  return { ...owner, [step.attrName]: updatedSiblings };
}

/**
 * Position of the row identified by `id`, or `-1`. Resolved through {@link rowId} rather than by comparing
 * `id` directly, so that a lookup and the URL segment that arrives back cannot drift apart on an entity
 * keyed by something else — `App Region` has no `id` at all and is identified by `type`.
 */
export function indexOfRow(rows: readonly EmbeddedRow[], id: string, referenceIdField?: string): number {
  if (!id) return -1;
  return rows.findIndex((row) => rowId(row, referenceIdField) === id);
}

export function findRow(rows: readonly EmbeddedRow[], id: string, referenceIdField?: string): EmbeddedRow | undefined {
  const index = indexOfRow(rows, id, referenceIdField);
  return index < 0 ? undefined : rows[index];
}

/** The value that identifies `row` in a URL segment — its `referenceIdField`, defaulting to `id`. */
export function rowId(row: EmbeddedRow, referenceIdField?: string): string {
  const value = (row as Payload)[referenceIdField ?? 'id'];
  return value === undefined || value === null ? '' : String(value);
}

export function appendRow(rows: readonly EmbeddedRow[], row: EmbeddedRow): EmbeddedRow[] {
  return [...rows, row];
}

/** Replaces the row identified by `id`; appends when it is not there yet, so an add and a re-save agree. */
export function replaceRow(rows: readonly EmbeddedRow[], id: string, row: EmbeddedRow, referenceIdField?: string): EmbeddedRow[] {
  const index = indexOfRow(rows, id, referenceIdField);
  if (index < 0) return appendRow(rows, row);

  const updatedRows = [...rows];
  updatedRows[index] = row;
  return updatedRows;
}

export function removeRow(rows: readonly EmbeddedRow[], id: string, referenceIdField?: string): EmbeddedRow[] {
  const index = indexOfRow(rows, id, referenceIdField);
  if (index < 0) return [...rows];

  return rows.filter((_, candidateIndex) => candidateIndex !== index);
}

/** Widens a row to the shape the store's API is written in; embedded rows always carry their key field. */
export function asPersisted<Entity extends BaseEntity>(row: EmbeddedRow): PersistedEntity<Entity> {
  return row as PersistedEntity<Entity>;
}

// region private helper functions
function asPayload(value: unknown): Payload | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Payload) : undefined;
}

function asArray(value: unknown): unknown[] | undefined {
  return Array.isArray(value) ? value : undefined;
}
// endregion
