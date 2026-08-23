import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { StateMachineDefinition } from '../../domain/definition/state-machine-definition';
import { createStateMachineDefinitionDescriptor } from '../../domain/definition/state-machine-definition.descriptors';
import { StateMachineDefinitionMapper } from '../../domain/definition/state-machine-definition.mapper';
import { StateMachineDefinitionService } from '../../domain/definition/state-machine-definition.service';
import { StateMachineDefinitionStore } from '../../domain/definition/state-machine-definition.store';
import { STATE_MODELER_TAB } from './state-modeler-tab';

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

  /**
   * The stock descriptor plus the State Modeler tab.
   *
   * The tab is added here rather than in `createStateMachineDefinitionDescriptor()`, which lives in
   * `domain/` and would then have to import a `feature/` component. `BaseEntityContainerComponent` is
   * mounted directly by {@link BASE_STATE_ROUTES} and resolves its descriptor from
   * `ACTIVE_ENTITY_FACADE`, so this is where `BaseEntityTabsComponent` reads `extraTabs` from.
   * `declaredDescriptor()` caches, so it runs once.
   */
  protected override createDescriptor(): BaseEntityDescriptor {
    const descriptor = createStateMachineDefinitionDescriptor();
    descriptor.extraTabs = [STATE_MODELER_TAB];
    return descriptor;
  }
}
