import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '../base-entity-service/base-entity-rest.service';
import type { EntityDefinition } from '../base-entity-definition/entity-definition';
import { ENTITY_SERVICE_ROOT_KEY } from '../base-entity-definition/entity-definition.service';
import { EntityDefinitionMapper } from './entity-definition.mapper';

/**
 * CRUD access to `/organizations/{orgKey}/entity-definitions` — the knowledge-layer resource of
 * `base-entity-api.yaml`, as the designer writes it. As in base-rule, base-app and base-state, the
 * organization is part of the configured service root, so the tenant is a deployment concern rather than
 * something every call has to carry.
 *
 * A second service over the same resource as {@link EntityDefinitionService}, and deliberately so — the two
 * answer different questions. That one reads **all** definitions in one call and caches them for the
 * session, because a run-time shell needs the whole set to resolve a `linkedEntityType` code and swallows a
 * failure as "no definitions". This one is a `BaseEntityRestService`, which is what gives a definition a
 * *store*: paged reads, `findById`, POST, PUT, DELETE, and errors that reach the screen instead of being
 * turned into an empty list. Merging them would mean either a designer that cannot report a failed save or
 * a shell that breaks a preview over one.
 *
 * `ENTITY_SERVICE_ROOT` is optional in `BaseConfiguration`: `serviceRootOf` falls back to
 * `APP_SERVICE_ROOT`, which is what every deployment of this workspace configures today.
 *
 * Nothing is added on top of the generic CRUD. The contract's incremental attribute endpoints
 * (`POST/PUT/DELETE .../attributes/{attributeCode}`) are not used: the attributes are edited as an embedded
 * list inside the definition's own form, so the whole definition is what gets PUT — the "PUT the whole
 * definition for bulk changes" path the contract names on `addAttribute` itself.
 */
@Injectable({ providedIn: 'root' })
export class EntityDefinitionAuthoringService extends BaseEntityRestService<EntityDefinition> {
  constructor(protected override entityMapper: EntityDefinitionMapper) {
    super(entityMapper, ENTITY_SERVICE_ROOT_KEY, 'entity-definitions');
  }
}
