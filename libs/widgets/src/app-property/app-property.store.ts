import { signalStore } from '@ngrx/signals';
import { BaseEntityContainerStore, BaseEntityStore, BaseEntityTabsStore, BaseFormNavigatorStore } from '@processpuzzle/base-entity';
import { ApplicationProperty } from './app-property';
import { ApplicationPropertyService } from './app-property.service';

export const ApplicationPropertyStore = signalStore(
  { providedIn: 'root' },
  BaseEntityStore<ApplicationProperty>(ApplicationProperty, ApplicationPropertyService),
  BaseFormNavigatorStore('ApplicationProperty'),
  BaseEntityTabsStore(),
  BaseEntityContainerStore(),
);
