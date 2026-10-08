import { Injectable } from '@angular/core';
import { BaseEntityMapper } from '@processpuzzle/base-entity';
import { EventAction, EventDefinition, EventKind } from './event-definition';

/** An event definition as `base-event-api.yaml` puts it on the wire. */
export interface EventDefinitionDto {
  id?: string;
  name?: string;
  description?: string;
  kind?: EventKind | string;
  subjectType?: string;
  action?: EventAction | string;
  state?: string;
  version?: number;
}

/**
 * Translates between the `EventDefinition` DTO and the entity the generated screens work with.
 *
 * Fields are listed one by one rather than spread, so a control the form may gain later cannot leak into
 * the payload unnoticed.
 *
 * `toDto` drops the subject fields of a non-`SYSTEM` event, and `state` unless the action is
 * `STATE_CHANGED`. base-entity has no conditional visibility, so a user who switches the kind keeps whatever
 * the hidden-in-meaning fields held; sending them would make the backend refuse a definition the form shows
 * as valid. Empty strings — what a cleared text box writes back — are sent as absent for the same reason.
 */
@Injectable({ providedIn: 'root' })
export class EventDefinitionMapper implements BaseEntityMapper<EventDefinition> {
  fromDto(dto: unknown): EventDefinition {
    const source = dto as EventDefinitionDto;
    return new EventDefinition({
      id: source.id,
      name: source.name,
      description: source.description,
      kind: source.kind as EventKind | undefined,
      subjectType: source.subjectType,
      action: source.action as EventAction | undefined,
      state: source.state,
      version: source.version,
    });
  }

  toDto(entity: EventDefinition): EventDefinitionDto {
    const isSystem = entity.kind === EventKind.SYSTEM;
    const action = isSystem ? presentOrUndefined(entity.action) : undefined;
    return {
      id: entity.id,
      name: entity.name,
      description: presentOrUndefined(entity.description),
      kind: entity.kind,
      subjectType: isSystem ? presentOrUndefined(entity.subjectType) : undefined,
      action,
      state: action === EventAction.STATE_CHANGED ? presentOrUndefined(entity.state) : undefined,
      version: entity.version,
    };
  }
}

function presentOrUndefined<T extends string>(value: T | undefined | null): T | undefined {
  return value === null || value === undefined || value.trim() === '' ? undefined : value;
}
