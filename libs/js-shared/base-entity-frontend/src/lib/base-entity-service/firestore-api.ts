import { InjectionToken } from '@angular/core';
import { collection, deleteDoc, doc, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where } from '@angular/fire/firestore';

/**
 * The subset of the Firestore module API that {@link BaseEntityFirestoreService} calls.
 */
export interface FirestoreApi {
  readonly collection: typeof collection;
  readonly deleteDoc: typeof deleteDoc;
  readonly doc: typeof doc;
  readonly getDoc: typeof getDoc;
  readonly getDocs: typeof getDocs;
  readonly limit: typeof limit;
  readonly orderBy: typeof orderBy;
  readonly query: typeof query;
  readonly setDoc: typeof setDoc;
  readonly updateDoc: typeof updateDoc;
  readonly where: typeof where;
}

/**
 * Firestore's free functions, reached through DI instead of imported at each call site.
 *
 * This exists so the adapter can be tested deterministically, and the indirection is the only way to get
 * that here. The suite used to `vi.mock('@angular/fire/firestore')` and assert on the mocked functions.
 * That is a bare specifier, which the Angular unit-test system does allow — but whether the replacement
 * reaches *this library's* modules is not stable: the builder may inline the barrel into the source bundle,
 * where a mock cannot touch it. The suite consequently passed or failed at roughly even odds on an
 * unchanged commit — surfacing either as the real `collection()` rejecting a plain object, or, since the
 * real `Firestore` class then became the token the service asked for, as
 * `NG0201: No provider found for Firestore`. CI failed on the coin flip.
 *
 * Nor can the barrel be hidden behind a relative re-export and that mocked instead: the runner rejects it
 * outright — *"vi.mock and related methods are not supported for relative imports with the Angular
 * unit-test system. Please use Angular TestBed for mocking dependencies."* This token is that advice taken.
 *
 * The default factory returns the real functions, so an application binds nothing.
 */
export const FIRESTORE_API = new InjectionToken<FirestoreApi>('base-entity FIRESTORE_API', {
  providedIn: 'root',
  factory: () => ({ collection, deleteDoc, doc, getDoc, getDocs, limit, orderBy, query, setDoc, updateDoc, where }),
});
