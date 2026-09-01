import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { EntityDefinition } from '../base-entity-definition/entity-definition';
import { BaseEntityContainerStore } from '../base-entity-container.store';
import { BaseEntityStore } from '../base-entity-store/base-entity.store';
import { BaseEntityTabsStore } from '../base-tabs/base-entity-tabs.store';
import { EntityDefinitionAuthoringService } from './entity-definition-authoring.service';

/**
 * The stock CRUD store. Like `StateMachineDefinitionStore` and unlike `AppDefinitionStore` there is no extra
 * feature to add: a definition's `status` is authored as an ordinary dropdown, not promoted through a
 * publish endpoint, and its `version` is an optimistic lock rather than a lifecycle.
 *
 * `providedIn: 'root'` because the store outlives the route: the designer's list, the attribute levels below
 * it and `EntityDefinitionContainerComponent` all read the same rows.
 */
export const EntityDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<EntityDefinition>(EntityDefinition, () => inject(EntityDefinitionAuthoringService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('EntityDefinition'),
);
