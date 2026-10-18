import { Injectable } from '@angular/core';
import type { BaseEntityMapper } from '@processpuzzle/base-entity';
import type { ReleaseState } from '@processpuzzle/util';
import { CalculationDefinition, type CalculationParameter } from './calculation-definition';

/** Wire shape of the contract's `CalculationDefinition`. */
export interface CalculationDefinitionDto {
  id?: string;
  name?: string | null;
  description?: string | null;
  source: string;
  definitionVersion?: number;
  parameters?: CalculationParameter[];
  outputEntity?: string | null;
  version?: number;
  releaseState?: ReleaseState;
}

/**
 * Between the wire and {@link CalculationDefinition}. Writes send the source and the version only — the contract's
 * `CalculationDefinitionInput` — since everything else is derived from the source on the server.
 */
@Injectable({ providedIn: 'root' })
export class CalculationDefinitionMapper implements BaseEntityMapper<CalculationDefinition> {
  fromDto(dto: unknown): CalculationDefinition {
    const source = dto as CalculationDefinitionDto;
    return new CalculationDefinition({
      id: source.id,
      name: source.name ?? undefined,
      description: source.description ?? undefined,
      source: source.source,
      definitionVersion: source.definitionVersion,
      parameters: source.parameters ?? [],
      outputEntity: source.outputEntity ?? undefined,
      version: source.version,
      releaseState: source.releaseState,
    });
  }

  toDto(entity: CalculationDefinition): CalculationDefinitionDto {
    return { source: entity.source, version: entity.version };
  }
}
