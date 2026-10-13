import { HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { definitionRead } from '@processpuzzle/util';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { EventDefinition } from './event-definition';
import { EventDefinitionMapper } from './event-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/event-definitions`. As in every other feature, the organization
 * is part of the configured service root, so the tenant is a deployment concern.
 *
 * `EVENT_SERVICE_ROOT` is optional: `serviceRootOf` falls back to `BACKEND_SERVICE_ROOT`, which is what
 * every deployment of this workspace configures today. The YAML `POST /import` the contract also declares
 * is a seeding path and has no screen, so nothing is added on top of the generic CRUD.
 */
@Injectable({ providedIn: 'root' })
export class EventDefinitionService extends BaseEntityRestService<EventDefinition> {
  /** Reads of definitions, addressed by the host to the draft or the released revision; see `definitionRevisionInterceptor`. */
  protected override readContext(): HttpContext {
    return definitionRead();
  }

  constructor(protected override entityMapper: EventDefinitionMapper) {
    super(entityMapper, 'EVENT_SERVICE_ROOT', 'event-definitions');
  }
}
