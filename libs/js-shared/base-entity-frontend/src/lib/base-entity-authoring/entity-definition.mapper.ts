import { Injectable } from '@angular/core';
import type { BaseEntityMapper } from '../base-entity.mapper';
import { EntityAttributeDefinition, EntityDefinition, type EntityDefinitionStatus, type EntityValueKind } from '../base-entity-definition/entity-definition';

/** `BaseEntityAttributeInput` of `base-entity-api.yaml`, plus the `id` a response carries. */
interface EntityAttributeDto {
  id?: string;
  code?: string;
  name?: string;
  description?: string;
  displayOrder?: number;
  valueKind?: EntityValueKind;
  formControlType?: string;
  isMultiValued?: boolean;
  required?: boolean;
  indexed?: boolean;
  defaultValue?: unknown;
  enumValues?: string[];
  linkedEntityType?: string;
  isLinkToDetails?: boolean;
}

/** `BaseEntityDefinition` on the way in, `BaseEntityDefinitionInput` on the way out. */
interface EntityDefinitionDto {
  id?: string;
  code?: string;
  name?: string;
  description?: string;
  status?: EntityDefinitionStatus;
  componentParents?: string[];
  isEmbedded?: boolean;
  attributes?: EntityAttributeDto[];
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between the `BaseEntityDefinition` DTO of `base-entity-api.yaml` and the entity the generated
 * screens author.
 *
 * Three things are worth knowing about it.
 *
 * **`id` is a mirror of `code`.** The contract addresses a definition by `code`
 * (`/entity-definitions/{code}`) while the generic screens address a record by `id` — so the mapper is
 * where the two meet, rather than every caller. It mirrors on the way *in* only: the request body is
 * `BaseEntityDefinitionInput`, which has no `id`, so `toDto` emits none and the path segment stays the sole
 * source of truth. The read-only uuid a response calls `id` is deliberately dropped, and can be, because
 * nothing sends it back. (base-state's mapper does emit an `id`, for a reason that does not apply here: it
 * used to be read by json-server, which no longer serves any platform feature.)
 *
 * **The nested rows are mapped element by element**, not passed through. An embedded row is edited as the
 * parsed JSON it arrived as, so a field the wire spelled differently from the model would leave its control
 * empty and silently drop the value on the next save. Mapping each row keeps that class of bug out of the
 * attribute descriptor even though nothing is spelled two ways today.
 *
 * **`PUT /entity-definitions/{code}` is a full replacement**, so `toDto` emits `attributes` unconditionally
 * — an absent list is an emptied definition, not an untouched one. The three fields the contract marks
 * `readOnly` (`version`, `createdAt`, `updatedAt`) are for the same reason *not* emitted: the form shows
 * them disabled, and sending a value back would be claiming to own something the backend assigns.
 */
@Injectable({ providedIn: 'root' })
export class EntityDefinitionMapper implements BaseEntityMapper<EntityDefinition> {
  fromDto(dto: unknown): EntityDefinition {
    const source = dto as EntityDefinitionDto;
    const code = source.code ?? source.id ?? '';
    return new EntityDefinition({
      id: code,
      code,
      name: source.name,
      description: source.description,
      status: source.status,
      componentParents: source.componentParents,
      isEmbedded: source.isEmbedded,
      attributes: (source.attributes ?? []).map(toAttribute),
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: EntityDefinition): EntityDefinitionDto {
    // Listed field by field rather than spread, so a control the form may gain later cannot leak into the
    // payload unnoticed — and so the `id` mirror stays out of it.
    return {
      code: entity.code || (entity.id ?? ''),
      name: entity.name,
      description: entity.description,
      status: entity.status,
      componentParents: entity.componentParents ?? [],
      isEmbedded: entity.isEmbedded ?? false,
      attributes: (entity.attributes ?? []).map(fromAttribute),
    };
  }
}

// region private helper functions
function toAttribute(dto: EntityAttributeDto): EntityAttributeDefinition {
  return new EntityAttributeDefinition({
    code: dto.code,
    name: dto.name,
    description: dto.description,
    displayOrder: dto.displayOrder,
    valueKind: dto.valueKind,
    formControlType: dto.formControlType,
    isMultiValued: dto.isMultiValued,
    required: dto.required,
    indexed: dto.indexed,
    defaultValue: dto.defaultValue,
    enumValues: dto.enumValues,
    linkedEntityType: dto.linkedEntityType,
    isLinkToDetails: dto.isLinkToDetails,
  });
}

/**
 * The four flags are written explicitly rather than left off when false, because the enclosing PUT is a
 * full replacement: an absent flag is an unset one, and the form's unticked checkbox has to say so.
 *
 * `id` is not emitted — `BaseEntityAttributeInput` has no such field, and `code` is what identifies the
 * row inside the definition. `description` is not emitted either, for the same reason: it is on the model
 * (see `EntityAttributeDefinition.description`) but not in the contract.
 */
function fromAttribute(attribute: EntityAttributeDefinition): EntityAttributeDto {
  return {
    code: attribute.code,
    name: attribute.name,
    displayOrder: attribute.displayOrder,
    valueKind: attribute.valueKind,
    formControlType: attribute.formControlType,
    isMultiValued: attribute.isMultiValued ?? false,
    required: attribute.required ?? false,
    indexed: attribute.indexed ?? false,
    defaultValue: attribute.defaultValue,
    enumValues: attribute.enumValues,
    linkedEntityType: attribute.linkedEntityType,
    isLinkToDetails: attribute.isLinkToDetails ?? false,
  };
}
// endregion
