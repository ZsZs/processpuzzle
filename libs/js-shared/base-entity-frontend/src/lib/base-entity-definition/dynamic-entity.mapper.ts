import { BaseEntityMapper } from '../base-entity.mapper';
import { DynamicEntity } from './entity-definition';

/** Envelope fields of the contract's `EntityObject` — everything that is not an authored attribute. */
const ID = 'id';
const VERSION = 'version';
const DEFINITION_CODE = 'entityDefinitionCode';
const PAYLOAD = 'payload';

/**
 * Between the contract's `EntityObject` envelope and the flat entity the generated screens bind to.
 *
 * The contract nests the attribute values in `payload`, keyed by attribute code. Nothing in the framework
 * can bind to that: `BaseEntityFormBuilder` adds a control per `attrName` and reads its initial value with
 * `Reflect.get(entity, attrName)`, `BaseEntityListComponent` reads a cell the same way, and RSQL filters
 * are built from `attrName` too. So the envelope is opened here, at the repository boundary, and closed
 * again on the way out — which is the only reason `BaseEntityListComponent` and `BaseEntityFormComponent`
 * need no knowledge of metadata-defined entities at all.
 *
 * `id` and `version` travel on the flat entity beside the attributes. `id` because `PersistedEntity`
 * requires it and the store keys rows by it; `version` because the update endpoint rejects a payload whose
 * version does not match the stored one, and the only place to carry it between a read and a write is the
 * entity the form edited. Neither can collide with an attribute unless a definition declares an attribute
 * of that name, in which case the envelope wins — see {@link fromDto}.
 */
export class DynamicEntityMapper implements BaseEntityMapper<DynamicEntity> {
  constructor(private readonly entityDefinitionCode: string) {}

  /**
   * `{id, version, payload: {...}}` → `{id, version, ...payload}`.
   *
   * The envelope is spread **after** the payload, so an attribute that happens to be called `id` or
   * `version` cannot shadow the row's identity or its lock. A tenant is free to declare such an attribute
   * — the backend stores it in the payload without complaint — and losing the real id to it would make the
   * row unaddressable.
   *
   * A response that is not an object yields an empty entity rather than throwing: `assertPersistedEntity`
   * in `BaseEntityRestService` is the check that turns a nonsense response into an error, and it gives a
   * better message than a `TypeError` from here.
   */
  fromDto(dto: unknown): DynamicEntity {
    const envelope = asRecord(dto);
    if (!envelope) return {};

    return {
      ...asRecord(envelope[PAYLOAD]),
      ...(typeof envelope[ID] === 'string' ? { [ID]: envelope[ID] } : {}),
      ...(typeof envelope[VERSION] === 'number' ? { [VERSION]: envelope[VERSION] } : {}),
    };
  }

  /**
   * `{id, version, ...attributes}` → `{entityDefinitionCode, version, payload: {...attributes}}`.
   *
   * One shape for both verbs, though the contract declares two: `EntityObjectInput` (POST) has
   * `entityDefinitionCode` + `payload`, `EntityObjectUpdate` (PUT) has `version` + `payload`. Emitting the
   * union sends each endpoint one field it does not declare, which Jackson ignores — Spring Boot leaves
   * `FAIL_ON_UNKNOWN_PROPERTIES` off. The alternative is a mapper that knows which verb is about to be
   * used, which `BaseEntityMapper` has no way to tell it and which would put HTTP knowledge in a mapper.
   *
   * `id` is deliberately **not** sent: it is a path segment on update and assigned by the server on
   * create, so a body carrying it could only ever disagree with the URL.
   */
  toDto(entity: DynamicEntity): unknown {
    const { id: _id, version, ...payload } = entity;

    return {
      [DEFINITION_CODE]: this.entityDefinitionCode,
      ...(typeof version === 'number' ? { version } : {}),
      [PAYLOAD]: payload,
    };
  }
}

function asRecord(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : undefined;
}
