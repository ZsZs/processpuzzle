import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { MATCHING_DEFAULTS, RecognitionProfile } from './recognition-profile';

interface MatchingSettingsDto {
  identifierWeight?: number;
  acceptScore?: number;
  acceptMargin?: number;
  sampleFps?: number;
}

export interface RecognitionProfileDto {
  id?: string;
  entityName?: string;
  name?: string;
  description?: string;
  detectorClass?: string;
  galleryAttributeKey?: string;
  identifierAttributeKey?: string;
  identifierPattern?: string;
  matching?: MatchingSettingsDto;
  orgKey?: string;
  version?: number;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * Translates between `ai-api.yaml`'s `RecognitionProfile` and the entity the generated screens edit.
 *
 * `id` mirrors `entityName` both ways, as `StateMachineDefinitionMapper` does and for the same reason: the
 * contract addresses a profile by `entityName`, the generic screens by `id`. The matching settings are
 * flattened on the way in and nested on the way out; a number the form hands back as text is converted
 * here; empty, nonnumeric or non-finite values fall back to the contract's default.
 *
 * `PUT /recognition-profiles/{entityName}` is a full replacement, so `toDto` emits every field.
 */
@Injectable({ providedIn: 'root' })
export class RecognitionProfileMapper implements BaseEntityMapper<RecognitionProfile> {
  fromDto(dto: unknown): RecognitionProfile {
    const source = dto as RecognitionProfileDto;
    const entityName = source.entityName ?? source.id ?? '';
    return new RecognitionProfile({
      id: entityName,
      entityName,
      name: source.name,
      description: source.description,
      detectorClass: source.detectorClass,
      galleryAttributeKey: source.galleryAttributeKey,
      identifierAttributeKey: source.identifierAttributeKey,
      identifierPattern: source.identifierPattern,
      identifierWeight: source.matching?.identifierWeight,
      acceptScore: source.matching?.acceptScore,
      acceptMargin: source.matching?.acceptMargin,
      sampleFps: source.matching?.sampleFps,
      orgKey: source.orgKey,
      version: source.version,
      createdAt: source.createdAt,
      updatedAt: source.updatedAt,
    });
  }

  toDto(entity: RecognitionProfile): RecognitionProfileDto {
    const entityName = entity.entityName || (entity.id ?? '');
    return {
      id: entityName,
      entityName,
      name: entity.name,
      description: blankToUndefined(entity.description),
      detectorClass: entity.detectorClass,
      galleryAttributeKey: entity.galleryAttributeKey,
      identifierAttributeKey: blankToUndefined(entity.identifierAttributeKey),
      identifierPattern: blankToUndefined(entity.identifierPattern),
      matching: {
        identifierWeight: toNumber(entity.identifierWeight, MATCHING_DEFAULTS.identifierWeight),
        acceptScore: toNumber(entity.acceptScore, MATCHING_DEFAULTS.acceptScore),
        acceptMargin: toNumber(entity.acceptMargin, MATCHING_DEFAULTS.acceptMargin),
        sampleFps: toNumber(entity.sampleFps, MATCHING_DEFAULTS.sampleFps),
      },
      orgKey: entity.orgKey,
      version: entity.version,
      createdAt: entity.createdAt,
      updatedAt: entity.updatedAt,
    };
  }
}

function toNumber(value: unknown, fallback: number): number {
  if ((typeof value !== 'number' && typeof value !== 'string') || value === '') return fallback;
  const parsed = typeof value === 'number' ? value : Number(value.replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : fallback;
}

function blankToUndefined(value: string | undefined): string | undefined {
  return value?.trim() ? value : undefined;
}
