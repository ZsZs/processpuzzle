import type { ReleaseState } from '@processpuzzle/util';
import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { ChatDefinition, ChatLifecycleKind, ChatMultiplicity, ChatSubjectKind } from './chat-definition';

/** A chat definition as `base-chat-api.yaml` puts it on the wire. */
export interface ChatDefinitionDto {
  key?: string;
  name?: string;
  description?: string;
  subjectType?: ChatSubjectKind | string;
  subjectDefinition?: string;
  multiplicity?: ChatMultiplicity | string;
  lifecycle?: ChatLifecycleKind | string;
  writableStates?: string[];
  readRoles?: string[];
  writeRoles?: string[];
  createRoles?: string[];
  includeSubjectOwner?: boolean;
  retentionDays?: number | null;
  version?: number;
  releaseState?: ReleaseState;
}

/**
 * Translates between the `ChatDefinition` DTO and the entity the generated screens work with: the contract's
 * `key` is the entity's `id`.
 *
 * `writableStates` is dropped unless the lifecycle is `STATE_BOUND`: base-entity has no conditional visibility,
 * so a user who switches the lifecycle keeps what the field held, and the backend refuses states on a chat
 * they do not apply to. The role lists are always sent — `PUT` replaces, so removing the last role has to send
 * `[]`. Blank strings and a non-positive retention are sent as absent.
 */
@Injectable({ providedIn: 'root' })
export class ChatDefinitionMapper implements BaseEntityMapper<ChatDefinition> {
  fromDto(dto: unknown): ChatDefinition {
    const source = dto as ChatDefinitionDto;
    return new ChatDefinition({
      id: source.key,
      name: source.name,
      description: source.description,
      subjectType: source.subjectType as ChatSubjectKind | undefined,
      subjectDefinition: source.subjectDefinition,
      multiplicity: source.multiplicity as ChatMultiplicity | undefined,
      lifecycle: source.lifecycle as ChatLifecycleKind | undefined,
      writableStates: source.writableStates ?? [],
      readRoles: source.readRoles ?? [],
      writeRoles: source.writeRoles ?? [],
      createRoles: source.createRoles ?? [],
      includeSubjectOwner: source.includeSubjectOwner,
      retentionDays: source.retentionDays ?? undefined,
      version: source.version,
      releaseState: source.releaseState,
    });
  }

  toDto(entity: ChatDefinition): ChatDefinitionDto {
    return {
      key: entity.id,
      name: entity.name,
      description: presentOrUndefined(entity.description),
      subjectType: entity.subjectType,
      subjectDefinition: entity.subjectDefinition,
      multiplicity: entity.multiplicity,
      lifecycle: entity.lifecycle,
      writableStates: entity.lifecycle === ChatLifecycleKind.STATE_BOUND ? [...(entity.writableStates ?? [])] : [],
      readRoles: [...(entity.readRoles ?? [])],
      writeRoles: [...(entity.writeRoles ?? [])],
      createRoles: [...(entity.createRoles ?? [])],
      includeSubjectOwner: entity.includeSubjectOwner === true,
      retentionDays: positiveOrUndefined(entity.retentionDays),
      version: entity.version,
    };
  }
}

function presentOrUndefined(value: string | undefined | null): string | undefined {
  return value === null || value === undefined || value.trim() === '' ? undefined : value;
}

/** A number text box writes back a string when edited, and an empty one when cleared. */
function positiveOrUndefined(value: number | string | undefined | null): number | undefined {
  const days = typeof value === 'string' ? Number(value) : value;
  return typeof days === 'number' && Number.isFinite(days) && days > 0 ? days : undefined;
}
