import { inject, Injectable, signal } from '@angular/core';
import { ActivatedRouteSnapshot, CanMatchFn, Route, Router, Routes } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AppDefinition } from '../../domain/app-definition';
import { AppDefinitionService } from '../../domain/app-definition.service';
import { AppShellRoutesFactory } from './app-shell-routes';

/**
 * The published revision of the application the runtime host renders — what end users see, as opposed to
 * the draft the designer edits and previews.
 *
 * Held here rather than in `AppDefinitionStore`, which is the designer's store of drafts: the two are
 * different revisions of the same entity, and one store holding both would let a save in the designer
 * leak into the live application before it was published.
 */
@Injectable({ providedIn: 'root' })
export class PublishedAppStore {
  private readonly appService = inject(AppDefinitionService);
  private readonly _published = signal<AppDefinition | undefined>(undefined);
  private readonly _loaded = signal(false);

  /** The published definition; `undefined` before the first load and while nothing has been published. */
  readonly published = this._published.asReadonly();
  /** Whether a load has finished, so a host can tell "not published" from "not asked yet". */
  readonly loaded = this._loaded.asReadonly();

  /**
   * Reads the published revision afresh. A transport failure is treated like "not published": the host then
   * renders its fallback rather than a dead URL, and the error itself has been reported by the interceptor.
   */
  async load(appId: string): Promise<AppDefinition | undefined> {
    let definition: AppDefinition | undefined;
    try {
      definition = await firstValueFrom(this.appService.findPublished(appId));
    } catch {
      definition = undefined;
    }
    this._published.set(definition);
    this._loaded.set(true);
    return definition;
  }

  /** Records that there is no application to load, as a finished load that found nothing. */
  async clear(): Promise<undefined> {
    this._published.set(undefined);
    this._loaded.set(true);
    return undefined;
  }
}

/**
 * Registers the **published** application's routes as the children of the runtime host's route — the
 * production counterpart of `appShellRoutesGuard`, and the same `canMatch` technique for the same reason:
 * `children` is read fresh on every recognition, and this runs, awaited, before the router reads it.
 *
 * **When it fetches.** On entering the host — the first load, or coming back from the designer or any other
 * screen outside it — and not on navigations inside it. Entering is the moment a newly published revision
 * has to show up: "Publish, then back to the application" is the designer's whole workflow. Inside, the
 * routes built on entry stay, so moving between the application's pages costs no request.
 *
 * `fallback` takes the place of the application's routes while nothing has been published — or a published
 * revision has no routes — so that the host's URL always matches something. Without it the host route would
 * fail to match, a catch-all would redirect back to it, and the navigation would never settle.
 *
 * `appId` is either the id itself or a function that finds it — for a host that cannot know it at build
 * time, because the organization's application is whichever one it kept. The function is called in the
 * guard's injection context, and an `undefined` answer means there is no application: the fallback.
 *
 * Always resolves to `true`: it does not decide whether the host may be entered, it fills in its children.
 */
export function publishedAppRoutesGuard(appId: string | AppIdResolver, fallback: Routes = []): CanMatchFn {
  return (route) => {
    const router = inject(Router);
    if (isActive(router.routerState.snapshot.root, route)) return true;

    // `inject` before the first await: an async callback loses the injection context at its first suspension.
    const store = inject(PublishedAppStore);
    const factory = inject(AppShellRoutesFactory);
    const id = typeof appId === 'string' ? Promise.resolve(appId) : appId();
    return id
      .then((resolved) => (resolved ? store.load(resolved) : store.clear()))
      .then(async (definition) => {
        const children = definition ? await factory.childrenFor(definition) : [];
        route.children = children.length ? children : fallback;
        return true;
      });
  };
}

/** Finds the id of the application a runtime host renders; `undefined` when the organization has none. */
export type AppIdResolver = () => Promise<string | undefined>;

/** Whether `route` is part of the router state being navigated away from — i.e. the host is already showing. */
function isActive(snapshot: ActivatedRouteSnapshot, route: Route): boolean {
  return snapshot.routeConfig === route || snapshot.children.some((child) => isActive(child, route));
}
