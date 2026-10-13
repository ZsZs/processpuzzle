import { HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { definitionRead } from '@processpuzzle/util';
import { BaseEntityRestService } from '@processpuzzle/base-entity';
import { ArtifactDefinition } from './artifact-definition';
import { ArtifactDefinitionMapper } from './artifact-definition.mapper';

/**
 * REST access to `/organizations/{orgKey}/artifacts`. A resource of its own rather than a
 * sub-resource of a workflow: the same artifact is produced by one workflow and consumed by another,
 * and both name it by id.
 */
@Injectable({ providedIn: 'root' })
export class ArtifactDefinitionService extends BaseEntityRestService<ArtifactDefinition> {
  /** Reads of definitions, addressed by the host to the draft or the released revision; see `definitionRevisionInterceptor`. */
  protected override readContext(): HttpContext {
    return definitionRead();
  }

  constructor(protected override entityMapper: ArtifactDefinitionMapper) {
    super(entityMapper, 'WORKFLOW_SERVICE_ROOT', 'artifacts');
  }
}
