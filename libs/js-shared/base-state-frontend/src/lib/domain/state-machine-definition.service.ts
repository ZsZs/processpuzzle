import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { StateMachineDefinition } from './state-machine-definition';
import { StateMachineDefinitionMapper } from './state-machine-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/state-machines`. As in base-rule and base-app, the organization
 * is part of the configured service root, so the tenant is a deployment concern rather than something
 * every call has to carry.
 *
 * `STATE_SERVICE_ROOT` is optional in `BaseConfiguration`: `serviceRootOf` falls back to
 * `APP_SERVICE_ROOT`, which is what every deployment of this workspace configures today. Naming a root
 * of its own is what lets base-state move to a host of its own later without any caller changing.
 *
 * Nothing is added on top of the generic CRUD. The operation layer of `base-state-api.yaml` — an entity
 * object's current state and `fireStateTransition` — is a different resource under
 * `/entities/{entityName}/{objectId}`, and belongs to whatever surface drives an object through its
 * machine rather than to the screens that author the machine.
 */
@Injectable({ providedIn: 'root' })
export class StateMachineDefinitionService extends BaseEntityRestService<StateMachineDefinition> {
  constructor(protected override entityMapper: StateMachineDefinitionMapper) {
    super(entityMapper, 'STATE_SERVICE_ROOT', 'state-machines');
  }
}
