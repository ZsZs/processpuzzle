import { inject, Injectable, Type } from '@angular/core';
import { BaseEntityDescriptor, BaseEntityFacade } from '@processpuzzle/base-entity';
import { CalculationDefinition } from '../domain/calculation-definition';
import { createCalculationDefinitionDescriptor } from '../domain/calculation-definition.descriptors';
import { CalculationDefinitionMapper } from '../domain/calculation-definition.mapper';
import { CalculationDefinitionService } from '../domain/calculation-definition.service';
import { CalculationDefinitionStore } from '../domain/calculation-definition.store';
import { CALCULATION_TABS } from './calculation-tabs';

@Injectable()
export class CalculationDefinitionFacade extends BaseEntityFacade<CalculationDefinition> {
  readonly entityType = CalculationDefinition;

  private readonly mapperRef = inject(CalculationDefinitionMapper);
  private readonly serviceRef = inject(CalculationDefinitionService);

  protected override createMapper() {
    return this.mapperRef;
  }

  protected override createService() {
    return this.serviceRef;
  }

  protected override createStoreClass(): Type<unknown> {
    return CalculationDefinitionStore;
  }

  /** The generated descriptor plus the Source, Decision tables and Evaluate tabs. */
  protected override createDescriptor(): BaseEntityDescriptor {
    const descriptor = createCalculationDefinitionDescriptor();
    descriptor.extraTabs = CALCULATION_TABS;
    return descriptor;
  }
}
