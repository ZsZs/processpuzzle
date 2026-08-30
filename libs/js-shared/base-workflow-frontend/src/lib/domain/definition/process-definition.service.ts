import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { ProcessDefinition } from './process-definition';
import { ProcessDefinitionMapper } from './process-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/processes`. As in base-rule, base-app and base-state, the
 * organization is part of the configured service root, so the tenant is a deployment concern rather
 * than something every call has to carry.
 *
 * `WORKFLOW_SERVICE_ROOT` is optional in `BaseConfiguration`: `serviceRootOf` falls back to
 * `APP_SERVICE_ROOT`, which is what every deployment of this workspace configures today. Naming a
 * root of its own is what lets base-workflow move to a host of its own later without any caller
 * changing.
 *
 * Nothing is added on top of the generic CRUD. The sub-resources of `base-workflow-api.yaml` —
 * `/processes/{id}/roles` and `/tasks` — are not used: the backend routes every mutation of a role or
 * a task through the process aggregate anyway, so that `version` stays an optimistic lock over the
 * whole definition, and the generic screens edit those rows as embedded components of this entity's
 * payload. `/import` and `/export` belong to a bulk-transfer surface this library does not have yet.
 */
@Injectable({ providedIn: 'root' })
export class ProcessDefinitionService extends BaseEntityRestService<ProcessDefinition> {
  constructor(protected override entityMapper: ProcessDefinitionMapper) {
    super(entityMapper, 'WORKFLOW_SERVICE_ROOT', 'processes');
  }
}
