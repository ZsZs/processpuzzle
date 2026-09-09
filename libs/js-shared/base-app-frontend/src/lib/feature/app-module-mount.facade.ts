import { Injectable } from '@angular/core';
import { BaseEntityDescriptor, EmbeddedEntityFacade } from '@processpuzzle/base-entity';
import { ModuleMount } from '../domain/app-definition';
import { createModuleMountDescriptor } from '../domain/module-mount.descriptors';

/**
 * A mount is a row of the `App Definition` document, so it is edited through that document — unlike the
 * `ModuleDefinition` it names, which is an aggregate of its own with its own endpoints.
 */
@Injectable()
export class AppModuleMountFacade extends EmbeddedEntityFacade<ModuleMount> {
  readonly entityType = ModuleMount;

  protected override createDescriptor(): BaseEntityDescriptor {
    return createModuleMountDescriptor();
  }
}
