import { ApplicationPropertyStore } from './app-property.store';
import { Provider } from '@angular/core';
import { ApplicationPropertyService } from './app-property.service';
import { ApplicationPropertyMapper } from './app-property.mapper';

/**
 * `ApplicationPropertyService` extends `BaseEntityRestService` and reads its endpoint from the runtime
 * configuration, so this store needs nothing but an injection context. It once took the `Firestore` token
 * and named it in `deps` — a leftover from when the service was Firestore-backed. The factory never used
 * the injected value, but naming it still *constructed* Firestore, which is enough to throw
 * `"projectId" not provided in firebase.initializeApp` in a deployment that no longer configures Firebase.
 *
 * @param _firestoreToken ignored. Accepted so existing call sites keep compiling; drop the argument.
 */
export function provideAppPropertyStore(_firestoreToken?: unknown): Provider[] {
  return [
    { provide: ApplicationPropertyService, useFactory: () => new ApplicationPropertyService(new ApplicationPropertyMapper()) },
    { provide: ApplicationPropertyStore, useFactory: () => new ApplicationPropertyStore(), deps: [ApplicationPropertyService] },
  ];
}
