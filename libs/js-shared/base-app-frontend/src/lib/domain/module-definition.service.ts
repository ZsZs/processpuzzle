import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { ModuleDefinition } from './module-definition';
import { ModuleDefinitionMapper } from './module-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/modules`. As for `AppDefinitionService`, the organization is
 * part of the configured service root (`APP_SERVICE_ROOT`), so the tenant is a deployment concern rather
 * than something every call has to carry.
 *
 * The URL variable is the module key — `ModuleDefinitionMapper` maps it onto `id`, which is what the
 * inherited `findById` builds `GET .../modules/{moduleKey}` from. That single-module read is also the call
 * the shell makes when something first navigates under a mount; see `ModuleLoader` in
 * `app-route-builder.ts`.
 */
@Injectable({ providedIn: 'root' })
export class ModuleDefinitionService extends BaseEntityRestService<ModuleDefinition> {
  constructor(protected override entityMapper: ModuleDefinitionMapper) {
    super(entityMapper, 'APP_SERVICE_ROOT', 'modules');
  }
}
