import { signalStore } from '@ngrx/signals';
import { inject } from '@angular/core';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore } from '@processpuzzle/base-entity';
import { ApplicationProperty } from './app-property';
import { ApplicationPropertyService } from './app-property.service';

export const ApplicationPropertyStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ApplicationProperty>(ApplicationProperty, () => inject(ApplicationPropertyService)),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
