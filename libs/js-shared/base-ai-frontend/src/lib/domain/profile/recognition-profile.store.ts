import { inject } from '@angular/core';
import { signalStore } from '@ngrx/signals';
import { withDevtools } from '@angular-architects/ngrx-toolkit';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { RecognitionProfile } from './recognition-profile';
import { RecognitionProfileService } from './recognition-profile.service';

/** The stock CRUD store: a profile has no lifecycle beyond create, edit and delete. */
export const RecognitionProfileStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<RecognitionProfile>(RecognitionProfile, () => inject(RecognitionProfileService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
  withDevtools('RecognitionProfile'),
);
