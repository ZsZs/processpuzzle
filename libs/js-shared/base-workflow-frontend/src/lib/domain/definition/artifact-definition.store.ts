import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { ArtifactDefinition } from './artifact-definition';
import { ArtifactDefinitionService } from './artifact-definition.service';

/** The stock CRUD store; an artifact definition has no lifecycle of its own — the *instances* it describes are what base-state governs. */
export const ArtifactDefinitionStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ArtifactDefinition>(ArtifactDefinition, () => inject(ArtifactDefinitionService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('ArtifactDefinition'),
);
