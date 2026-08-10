import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { Artifact } from './base-artifact';
import { BaseArtifactService } from './base-artifact.service';

export const BaseArtifactStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<Artifact>(Artifact, () => inject(BaseArtifactService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('BaseArtifact'),
);
