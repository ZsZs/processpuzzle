import { Injectable } from '@angular/core';
import type { BaseEntityMapper } from '../base-entity.mapper';
import type { DateFormat } from '../base-entity/date-format';
import { isBoundedByMaxOccurs, MULTIPLICITIES, type Multiplicity } from '../base-entity/multiplicity';
import type { ReleaseState } from '@processpuzzle/util';
import type { MapProvider } from '../base-form/address/address-map';
import {
  EntityAttributeDefinition,
  EntityDefinition,
  type EntityDefinitionStatus,
  type EntitySharing,
  type EntityValueKind,
} from '../base-entity-definition/entity-definition';

/** `BaseEntityAttributeInput` of `base-entity-api.yaml`, plus the `id` a response carries. */
interface EntityAttributeDto {
  id?: string;
  code?: string;
  name?: string;
  description?: string;
  displayOrder?: number;
  valueKind?: EntityValueKind;
  formControlType?: string;
  multiplicity?: Multiplicity;
  maxOccurs?: number;
  required?: boolean;
  indexed?: boolean;
  defaultValue?: unknown;
  enumValues?: string[];
  dateFormat?: DateFormat;
  linkedEntityType?: string;
  isLinkToDetails?: boolean;
  autosizeColumn?: boolean;
  defaultAvatar?: string;
  showMap?: boolean;
  mapProvider?: MapProvider;
  defaultCountry?: string;
  quantityKind?: string;
  allowedUnits?: string[];
  defaultUnit?: string;
  scale?: number;
  allowedCurrencies?: string[];
  defaultCurrency?: string;
  dimensionCount?: 2 | 3;
}

/** `BaseEntityDefinition` on the way in, `BaseEntityDefinitionInput` on the way out. */
interface EntityDefinitionDto {
  id?: string;
  code?: string;
  name?: string;
  description?: string;
  status?: EntityDefinitionStatus;
  releaseState?: ReleaseState;
  componentParents?: string[];
  isEmbedded?: boolean;
  canContextObject?: boolean;
  sharing?: EntitySharing;
  identifiers?: string[][];
  layout?: string[][];
  sharedFrom?: string;
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
 * `readOnly` (`version`, `createdAt`, `updatedAt`, and `releaseState`) are for the same reason *not* emitted: the form shows
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
      releaseState: source.releaseState,
      componentParents: source.componentParents,
      isEmbedded: source.isEmbedded,
      canContextObject: source.canContextObject,
      sharing: source.sharing,
      identifiers: source.identifiers,
      layout: source.layout,
      sharedFrom: source.sharedFrom,
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
      canContextObject: entity.canContextObject ?? false,
      // Carried, not authored here: the form has no control for it, and a save without it would unshare the type.
      sharing: entity.sharing,
      // Likewise carried: the natural keys of the type, which only the starter's YAML authors for now.
      identifiers: entity.identifiers,
      // Carried like the keys: authored in the starter's YAML, and a save without it would flatten the form.
      layout: entity.layout,
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
    multiplicity: dto.multiplicity,
    maxOccurs: dto.maxOccurs,
    required: dto.required,
    indexed: dto.indexed,
    defaultValue: dto.defaultValue,
    enumValues: dto.enumValues,
    dateFormat: dto.dateFormat,
    dateStyle: dto.dateFormat?.dateStyle,
    timeStyle: dto.dateFormat?.timeStyle,
    linkedEntityType: dto.linkedEntityType,
    isLinkToDetails: dto.isLinkToDetails,
    autosizeColumn: dto.autosizeColumn,
    defaultAvatar: dto.defaultAvatar,
    showMap: dto.showMap,
    mapProvider: dto.mapProvider,
    defaultCountry: dto.defaultCountry,
    // A JSON null is an option not set; the model knows only "absent".
    quantityKind: dto.quantityKind ?? undefined,
    allowedUnits: dto.allowedUnits ?? undefined,
    defaultUnit: dto.defaultUnit ?? undefined,
    scale: dto.scale ?? undefined,
    allowedCurrencies: dto.allowedCurrencies ?? undefined,
    defaultCurrency: dto.defaultCurrency ?? undefined,
    dimensionCount: dto.dimensionCount ?? undefined,
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
    ...multiplicityOf(attribute),
    required: attribute.required ?? false,
    indexed: attribute.indexed ?? false,
    defaultValue: attribute.defaultValue,
    enumValues: attribute.enumValues,
    dateFormat: dateFormatOf(attribute),
    linkedEntityType: attribute.linkedEntityType,
    isLinkToDetails: attribute.isLinkToDetails ?? false,
    autosizeColumn: attribute.autosizeColumn ?? false,
    defaultAvatar: attribute.defaultAvatar,
    ...addressOptionsOf(attribute),
    ...measureOptionsOf(attribute),
  };
}

/**
 * The options of `QUANTITY`, `MONEY` and `DIMENSIONS` — each only for the control that uses it, since the backend
 * rejects the others. The PUT is a full replacement, so what the form leaves empty is simply not sent: blank text is
 * no option, an empty list is none, and the number boxes' text is coerced (a NaN is no option either).
 */
function measureOptionsOf(attribute: EntityAttributeDefinition): Partial<EntityAttributeDto> {
  switch (attribute.formControlType) {
    case 'QUANTITY': {
      const quantityKind = attribute.quantityKind?.trim();
      return { ...(quantityKind ? { quantityKind } : {}), ...unitOptionsOf(attribute) };
    }
    case 'DIMENSIONS': {
      const dimensionCount = numberOf(attribute.dimensionCount);
      return { ...unitOptionsOf(attribute), ...(dimensionCount === undefined ? {} : { dimensionCount: dimensionCount as 2 | 3 }) };
    }
    case 'MONEY':
      return currencyOptionsOf(attribute);
    default:
      return {};
  }
}

/** What `QUANTITY` and `DIMENSIONS` share: the units on offer, the one to start in and the decimal places. */
function unitOptionsOf(attribute: EntityAttributeDefinition): Partial<EntityAttributeDto> {
  const defaultUnit = attribute.defaultUnit?.trim();
  const scale = numberOf(attribute.scale);
  return {
    ...(attribute.allowedUnits?.length ? { allowedUnits: attribute.allowedUnits } : {}),
    ...(defaultUnit ? { defaultUnit } : {}),
    ...(scale === undefined ? {} : { scale }),
  };
}

function currencyOptionsOf(attribute: EntityAttributeDefinition): Partial<EntityAttributeDto> {
  const defaultCurrency = attribute.defaultCurrency?.trim();
  return {
    ...(attribute.allowedCurrencies?.length ? { allowedCurrencies: attribute.allowedCurrencies } : {}),
    ...(defaultCurrency ? { defaultCurrency } : {}),
  };
}

/** A number typed into a text box may come back as a string; blank or not a number is no value. */
function numberOf(raw: unknown): number | undefined {
  if (raw === null || raw === undefined || raw === '') return undefined;
  const value = Number(raw);
  return Number.isFinite(value) ? value : undefined;
}

/**
 * The map options for `ADDRESS` only — the backend rejects them on any other control. `showMap` is written
 * explicitly for the same full-replacement reason as the flags; a blank country is no default.
 */
function addressOptionsOf(attribute: EntityAttributeDefinition): Pick<EntityAttributeDto, 'showMap' | 'mapProvider' | 'defaultCountry'> {
  if (attribute.formControlType !== 'ADDRESS') return {};
  const showMap = attribute.showMap ?? false;
  return {
    showMap,
    ...(showMap && attribute.mapProvider ? { mapProvider: attribute.mapProvider } : {}),
    ...(attribute.defaultCountry?.trim() ? { defaultCountry: attribute.defaultCountry.trim().toUpperCase() } : {}),
  };
}

/**
 * Explicit or absent, never a stale leftover: the PUT is a full replacement, so an absent `multiplicity` is a
 * single value. `maxOccurs` is sent only for the `x` forms — the backend rejects it on the others — and is
 * coerced because the number text box may hand back a string.
 */
function multiplicityOf(attribute: EntityAttributeDefinition): Pick<EntityAttributeDto, 'multiplicity' | 'maxOccurs'> {
  const multiplicity = MULTIPLICITIES.includes(attribute.multiplicity as Multiplicity) ? attribute.multiplicity : undefined;
  if (!multiplicity) return {};
  const raw: unknown = attribute.maxOccurs;
  const maxOccurs = raw == null || raw === '' ? NaN : Number(raw);
  if (!isBoundedByMaxOccurs(multiplicity) || !Number.isFinite(maxOccurs)) return { multiplicity };
  return { multiplicity, maxOccurs };
}

/**
 * The form's `dateStyle` / `timeStyle` folded into the contract's `dateFormat`. A style that does not apply
 * to the value kind is dropped rather than sent, so changing a DATE_TIME attribute to TEXT — or to DATE —
 * does not turn the save into a 422 over a dropdown the user can no longer see a reason for.
 */
function dateFormatOf(attribute: EntityAttributeDefinition): DateFormat | undefined {
  if (attribute.valueKind !== 'DATE' && attribute.valueKind !== 'DATE_TIME') return undefined;
  const dateStyle = attribute.dateStyle || undefined;
  const timeStyle = attribute.valueKind === 'DATE' ? undefined : attribute.timeStyle || undefined;
  if (!dateStyle && !timeStyle) return undefined;
  return { ...(dateStyle && { dateStyle }), ...(timeStyle && { timeStyle }) };
}
// endregion
