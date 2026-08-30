import { Injectable } from '@angular/core';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { ArtifactDefinition } from './artifact-definition';
import { ArtifactDefinitionMapper } from './artifact-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/artifacts`. A resource of its own rather than a
 * sub-resource of a process: the same artifact is produced by one process and consumed by another,
 * and both name it by id.
 */
@Injectable({ providedIn: 'root' })
export class ArtifactDefinitionService extends BaseEntityRestService<ArtifactDefinition> {
  constructor(protected override entityMapper: ArtifactDefinitionMapper) {
    super(entityMapper, 'WORKFLOW_SERVICE_ROOT', 'artifacts');
  }
}
