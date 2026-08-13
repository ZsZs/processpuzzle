import { ApplicationPropertyStore } from './app-property.store';
import { Provider, ProviderToken } from '@angular/core';
import { Firestore } from '@angular/fire/firestore';
import { ApplicationPropertyService } from './app-property.service';
import { ApplicationPropertyMapper } from './app-property.mapper';

export function provideAppPropertyStore(firestoreToken: ProviderToken<Firestore> = Firestore): Provider[] {
  return [
    { provide: ApplicationPropertyService, useFactory: () => new ApplicationPropertyService(new ApplicationPropertyMapper()), deps: [firestoreToken] },
    { provide: ApplicationPropertyStore, useFactory: () => new ApplicationPropertyStore(), deps: [ApplicationPropertyService] },
  ];
}
