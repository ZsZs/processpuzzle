import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { ArtifactDefinition } from '../../domain/definition/artifact-definition';
import { createArtifactDefinitionDescriptor } from '../../domain/definition/artifact-definition.descriptors';
import { ArtifactDefinitionMapper } from '../../domain/definition/artifact-definition.mapper';
import { ArtifactDefinitionService } from '../../domain/definition/artifact-definition.service';
import { ArtifactDefinitionStore } from '../../domain/definition/artifact-definition.store';

@Injectable()
export class ArtifactDefinitionFacade extends BaseEntityFacade<ArtifactDefinition> {
  readonly entityType = ArtifactDefinition;

  private readonly mapperRef = inject(ArtifactDefinitionMapper);
  private readonly serviceRef = inject(ArtifactDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return ArtifactDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createArtifactDefinitionDescriptor();
  }
}
