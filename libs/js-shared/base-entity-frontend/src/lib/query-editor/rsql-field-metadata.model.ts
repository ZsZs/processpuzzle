/**
 * Contract for exposing entity field metadata to the RSQL editor.
 * Wire this up to whatever already feeds BASE_ENTITY_STORE_REGISTRY / your
 * entity schema so field names, types and operators stay in sync with the backend.
 */

export type RsqlFieldType = 'string' | 'number' | 'boolean' | 'date' | 'datetime' | 'enum';

export interface RsqlFieldMetadata {
  name: string;
  type: RsqlFieldType;
  /** Restrict to a subset of RSQL_OPERATORS if not all operators are valid for this field */
  operators?: string[];
  /** Required when type === 'enum' */
  enumValues?: string[];
  /** Optional human-readable label shown in autocomplete */
  label?: string;
}

export const RSQL_OPERATORS_BY_TYPE: Record<RsqlFieldType, string[]> = {
  string: ['==', '!=', '=in=', '=out=', '=like='],
  number: ['==', '!=', '=gt=', '=ge=', '=lt=', '=le=', '=in=', '=out='],
  boolean: ['==', '!='],
  date: ['==', '!=', '=gt=', '=ge=', '=lt=', '=le='],
  datetime: ['==', '!=', '=gt=', '=ge=', '=lt=', '=le='],
  enum: ['==', '!=', '=in=', '=out='],
};

export abstract class RsqlFieldMetadataProvider {
  abstract getFields(): RsqlFieldMetadata[];
  abstract getField(name: string): RsqlFieldMetadata | undefined;
}
