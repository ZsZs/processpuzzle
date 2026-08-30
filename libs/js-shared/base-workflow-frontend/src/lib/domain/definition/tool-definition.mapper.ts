import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { AuthType, HttpMethod, ToolAuthConfig, ToolDefinition, ToolOperation } from './tool-definition';

// region wire shapes
interface ToolOperationDto {
  id?: string;
  method?: HttpMethod;
  path?: string;
  description?: string;
  payloadTemplate?: string;
  /** `integer[]` by contract. The model holds strings — see {@link ToolOperation.expectedStatusCodes}. */
  expectedStatusCodes?: number[];
}

interface ToolDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  baseUrl?: string;
  auth?: ToolAuthConfig;
  operations?: ToolOperationDto[];
  version?: number;
  createdAt?: string;
}
// endregion

/**
 * Translates between the `ToolDefinition` DTO of `base-workflow-api.yaml` and the entity the
 * generated screens work with. Two translations happen here that the workflow mapper does not need.
 *
 * **`auth` is flattened and re-nested.** `fromDto` lifts `type` and `secretRef` onto the entity so
 * the form can address them, and keeps the object they came from; `toDto` merges the two edited
 * fields *onto* that object rather than replacing it, so a field a later contract version adds
 * survives the full-replacement PUT even though no control here knows about it. Same shape as
 * base-app's `mergeTheme`.
 *
 * **`expectedStatusCodes` crosses a type boundary.** The contract says `integer[]`; the only control
 * that edits a flat list is `TAGS`, and it emits strings. So the model holds `string[]` and the
 * conversion lives here: anything that is not a finite integer is dropped rather than sent as `NaN`,
 * and an empty list is omitted so the backend applies its own default of `[200, 201, 204]` instead of
 * being told that *no* status code counts as success.
 */
@Injectable({ providedIn: 'root' })
export class ToolDefinitionMapper implements BaseEntityMapper<ToolDefinition> {
  fromDto(dto: unknown): ToolDefinition {
    const source = dto as ToolDefinitionDto;
    const auth = source.auth;
    return new ToolDefinition({
      id: source.id,
      name: source.name,
      description: source.description,
      baseUrl: source.baseUrl,
      type: auth?.type as AuthType | undefined,
      secretRef: auth?.secretRef,
      // Kept so toDto can merge onto it rather than replace it.
      auth,
      operations: (source.operations ?? []).map(toToolOperation),
      version: source.version,
      createdAt: source.createdAt,
    });
  }

  toDto(entity: ToolDefinition): ToolDefinitionDto {
    // Listed field by field rather than spread, so a control the form may gain later cannot leak into
    // the payload unnoticed — and so the two flattened fields never appear at the top level.
    return {
      id: entity.id,
      name: entity.name,
      description: entity.description,
      baseUrl: entity.baseUrl,
      auth: mergeAuth(entity),
      operations: (entity.operations ?? []).map(fromToolOperation),
      version: entity.version,
      createdAt: entity.createdAt,
    };
  }
}

// region private helper functions
function mergeAuth(entity: ToolDefinition): ToolAuthConfig {
  return { ...entity.auth, type: entity.type, secretRef: entity.secretRef };
}

function toToolOperation(dto: ToolOperationDto): ToolOperation {
  return new ToolOperation({
    id: dto.id,
    method: dto.method,
    path: dto.path,
    description: dto.description,
    payloadTemplate: dto.payloadTemplate,
    expectedStatusCodes: dto.expectedStatusCodes?.map((statusCode) => String(statusCode)),
  });
}

function fromToolOperation(operation: ToolOperation): ToolOperationDto {
  const statusCodes = toStatusCodes(operation.expectedStatusCodes);
  return {
    id: operation.id,
    method: operation.method,
    path: operation.path,
    description: operation.description,
    payloadTemplate: operation.payloadTemplate,
    // Omitted when empty rather than sent as `[]`: an empty array means "no status code is a success",
    // while an absent one lets the backend apply its documented default.
    expectedStatusCodes: statusCodes.length ? statusCodes : undefined,
  };
}

/**
 * The chips a user typed, as numbers.
 *
 * `Number.isInteger` rather than a bare `Number()` cast, because a chip list accepts any text and an
 * `NaN` in a JSON body is serialized as `null` — which the backend would then have to second-guess.
 *
 * The blank filter is not redundant with it: `Number('')` is `0`, and `0` *is* an integer, so a chip
 * the user emptied would otherwise be sent as the status code zero.
 */
function toStatusCodes(chips: string[] | undefined): number[] {
  return (chips ?? [])
    .filter((chip) => chip.trim() !== '')
    .map((chip) => Number(chip))
    .filter((statusCode) => Number.isInteger(statusCode));
}
// endregion
