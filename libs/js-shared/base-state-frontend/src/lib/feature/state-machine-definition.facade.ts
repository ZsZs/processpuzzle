import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { StateMachineDefinition } from '../domain/state-machine-definition';
import { createStateMachineDefinitionDescriptor } from '../domain/state-machine-definition.descriptors';
import { StateMachineDefinitionMapper } from '../domain/state-machine-definition.mapper';
import { StateMachineDefinitionService } from '../domain/state-machine-definition.service';
import { StateMachineDefinitionStore } from '../domain/state-machine-definition.store';

@Injectable()
export class StateMachineDefinitionFacade extends BaseEntityFacade<StateMachineDefinition> {
  readonly entityType = StateMachineDefinition;

  private readonly mapperRef = inject(StateMachineDefinitionMapper);
  private readonly serviceRef = inject(StateMachineDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return StateMachineDefinitionStore;
  }

  protected override createDescriptor(): BaseEntityDescriptor {
    return createStateMachineDefinitionDescriptor();
  }
}
