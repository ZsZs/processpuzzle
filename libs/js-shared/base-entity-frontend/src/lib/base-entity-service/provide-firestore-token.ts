import { Provider } from '@angular/core';
import { Firestore } from '@angular/fire/firestore';
import { FIRESTORE } from './firestore.token';

/**
 * Binds {@link FIRESTORE} to the instance `provideFirestore(...)` registered, for an application that
 * deploys `BaseEntityFirestoreService` against a real Firestore.
 *
 * One line next to the `provideFirestore(...)` call:
 * ```ts
 * providers: [provideFirebaseApp(() => initializeApp(config)), provideFirestore(() => getFirestore()), provideFirestoreToken()]
 * ```
 *
 * It lives in its own file on purpose. `firestore.token.ts` must not import `@angular/fire/firestore` at
 * run-time — see the note there — and this is where the one import that has to exist is confined.
 */
export function provideFirestoreToken(): Provider {
  return { provide: FIRESTORE, useExisting: Firestore };
}
