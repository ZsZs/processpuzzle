import { InjectionToken } from '@angular/core';
import type { Firestore } from '@angular/fire/firestore';

/**
 * The Firestore instance {@link BaseEntityFirestoreService} reads and writes through.
 *
 * The service used to inject `@angular/fire`'s `Firestore` class directly. Angular matches providers by
 * object identity, and a bare specifier does not always resolve to one object: mock the barrel, or let a
 * build inline it on one side of the graph and import it on the other, and the module the service imports
 * is a second instance of the same file. The class is then two distinct tokens — one registered, the other
 * asked for — and DI fails with `NG0201: No provider found for Firestore`.
 *
 * An `InjectionToken` declared here is a single object owned by this library. Note the `import type`: it
 * carries no runtime dependency on `@angular/fire/firestore`, which is what keeps this module out of the
 * blast radius when a test replaces the barrel, and so keeps the token one object.
 *
 * Applications that deploy on Firestore bind it with `provideFirestoreToken()`, next to their existing
 * `provideFirestore(...)` call.
 */
export const FIRESTORE = new InjectionToken<Firestore>('base-entity FIRESTORE');
