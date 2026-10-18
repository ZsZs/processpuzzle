import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { CalculationDefinition } from './calculation-definition';
import { CalculationDefinitionService } from './calculation-definition.service';

/** The stock CRUD store; the three extra tabs read and save the current definition through it. */
export const CalculationDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<CalculationDefinition>(CalculationDefinition, () => inject(CalculationDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('CalculationDefinition'),
);
