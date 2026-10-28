import { inject, Injectable, Injector, Provider } from '@angular/core';
import { CanMatchFn, Routes } from '@angular/router';
import { BaseUrlSegments } from '@processpuzzle/base-entity';
import { APP_ASSET_CONTEXT } from '@processpuzzle/widgets';
import { TranslocoService } from '@jsverse/transloco';
import { TranslocoHttpLoader } from '@processpuzzle/util';
import { firstValueFrom } from 'rxjs';
import { AppDefinition } from '../../domain/app-definition';
import { AppDefinitionService } from '../../domain/app-definition.service';
import { AppDefinitionStore } from '../../domain/app-definition.store';
import { ModuleDefinition } from '../../domain/module-definition';
import { ModuleDefinitionService } from '../../domain/module-definition.service';
import { ModuleDefinitionStore } from '../../domain/module-definition.store';
import { appAssetContextOf } from '../../domain/app-asset-context';
import { AppAssetService } from '../../domain/app-asset.service';
import { moduleAssetContextOf } from '../../domain/module-asset-context';
import { ModuleAssetService } from '../../domain/module-asset.service';
import { ModuleTranslationBundle } from '../../domain/module-translation';
import { ModuleTranslationService } from '../../domain/module-translation.service';
import { buildAppRoutes, ModuleLoader, ModuleTranslationLoader } from '../app-route-builder';
import { AppRouteRenderer } from '../app-route-renderer';

/**
 * Turns one `AppDefinition` into the `Routes` that render its screens — the missing consumer of
 * {@link buildAppRoutes}, which until now produced routes nothing registered.
 *
 * Separate from the guard below so the interesting part is testable without a router: this class answers
 * "what routes does app X have", and the guard is only the two lines that hand them to Angular.
 */
@Injectable({ providedIn: 'root' })
export class AppShellRoutesFactory {
  // The two stores — and the translation services, which a host that mounts no module never needs — are read on
  // first use rather than injected here. They are the designer's stores of
  // drafts, and each loads every definition the moment it is created — which a runtime host, building
  // the routes of the published revision it already holds, must not set off on its way past.
  private readonly injector = inject(Injector);
  private readonly appService = inject(AppDefinitionService);
  private readonly moduleService = inject(ModuleDefinitionService);
  private readonly renderRoute = inject(AppRouteRenderer).render;

  async childrenOf(appId: string): Promise<Routes> {
    const definition = await this.definitionOf(appId);
    return definition ? this.childrenFor(definition) : [];
  }

  /**
   * The routes of a definition already in hand — the published one the runtime host fetched, or the draft
   * {@link childrenOf} found. One builder for both is what keeps the Preview and the live application from
   * rendering the same definition differently.
   *
   * A definition read through `/layout` brings its mounted modules along, from its own side — the released
   * modules with the published revision — and those are the only ones it mounts. Fetching a module instead
   * would read its draft, and the live application would show edits nobody has released.
   */
  async childrenFor(definition: AppDefinition): Promise<Routes> {
    const loadModule = definition.mountedModules ? inlineModules(definition.mountedModules) : this.loadModule;
    // The released modules come with the published layout, so their translations and assets are the released
    // ones too; a draft definition — the designer's Preview — reads the draft ones a user just uploaded.
    const revision = definition.mountedModules ? 'released' : 'draft';
    const loadTranslations = this.translationsOf(revision);
    const moduleAssets = (moduleKey: string) => moduleAssetContextOf(this.injector.get(ModuleAssetService), moduleKey, revision);
    const built = await buildAppRoutes({ routes: definition.routes, modules: definition.modules, loadModule, loadTranslations, moduleAssets }, this.renderRoute);
    // Provided on every top-level route, the mounts included, so that an `app:` value is found on the application's
    // own pages and below a module alike.
    const appAssets: Provider = { provide: APP_ASSET_CONTEXT, useFactory: () => appAssetContextOf(this.injector.get(AppAssetService), definition.id, revision) };
    const withAssets = built.map((route) => ({ ...route, providers: [...(route.providers ?? []), appAssets] }));
    return withAssets.length ? [indexRoute(definition, withAssets), ...withAssets] : withAssets;
  }

  /**
   * Where a mounted module's translations come from: whatever the application or the seeded bundles already
   * serve for the scope, under what the module's own uploaded files say. The shipped bundle stays underneath
   * so that a module translated the way every module was before files could be uploaded — a seeded
   * `order_admin` bundle — keeps its words.
   *
   * A language the module has nothing uploaded for answers with the shipped bundle alone, and a failed read
   * of the uploaded one is no reason to fail the load: the titles keep their authored text.
   */
  private translationsOf(revision: 'draft' | 'released'): ModuleTranslationLoader {
    const languages = () => this.languages();
    return {
      get languages() {
        return languages();
      },
      load: async (moduleKey, scope, locale) => {
        const [shipped, uploaded] = await Promise.all([
          firstValueFrom(this.injector.get(TranslocoHttpLoader).getTranslation(`${scope}/${locale}`, { scope })),
          this.uploaded(moduleKey, locale, revision),
        ]);
        return { ...shipped, ...uploaded };
      },
    };
  }

  private async uploaded(moduleKey: string, locale: string, revision: 'draft' | 'released'): Promise<ModuleTranslationBundle> {
    try {
      return await firstValueFrom(this.injector.get(ModuleTranslationService).bundle(moduleKey, locale, revision));
    } catch {
      return {};
    }
  }

  /** The languages transloco is configured for, the active and the fallback one included. */
  private languages(): string[] {
    const transloco = this.injector.get(TranslocoService);
    const available = transloco.getAvailableLangs().map((lang) => (typeof lang === 'string' ? lang : lang.id));
    return [...new Set([...available, transloco.getActiveLang(), 'en'])];
  }

  /**
   * Store first, endpoint second.
   *
   * The store holds the newest definition immediately after a Save, so the next navigation inside the
   * preview picks up a route the designer just added — without a second GET, and without this having to
   * know that a save happened. The fetch is for the case the store cannot serve: a deep link or a reload,
   * where nothing has been loaded yet.
   *
   * A failure resolves to `undefined` rather than propagating. A guard that rejects fails the whole
   * navigation, and an app whose definition cannot be read should render an empty shell, not a dead URL.
   */
  private async definitionOf(appId: string): Promise<AppDefinition | undefined> {
    const known = this.injector.get(AppDefinitionStore).loadById(appId);
    if (known) return known;
    return (await this.fetch(() => firstValueFrom(this.appService.findById(appId)))) as AppDefinition | undefined;
  }

  /**
   * The {@link ModuleLoader} `buildAppRoutes` calls for a mount whose module it has not got. Resolving to
   * `undefined` is deliberate and is what the contract asks for: a `ModuleMount` naming a module nobody has
   * authored yet is a validation *warning*, so it must contribute no routes rather than break navigation.
   */
  private loadModule = async (moduleKey: string): Promise<ModuleDefinition | undefined> => {
    const known = this.injector.get(ModuleDefinitionStore).loadById(moduleKey);
    if (known) return known;
    return (await this.fetch(() => firstValueFrom(this.moduleService.findById(moduleKey)))) as ModuleDefinition | undefined;
  };

  /** `findById` resolves to `void` for a missing record and rejects on a transport error; both mean "no". */
  private async fetch<Result>(read: () => Promise<Result | void>): Promise<Result | undefined> {
    try {
      return (await read()) || undefined;
    } catch {
      return undefined;
    }
  }
}

/** A {@link ModuleLoader} over the modules a layout carried; a mount naming any other module shows nothing. */
function inlineModules(modules: ModuleDefinition[]): ModuleLoader {
  return async (moduleKey) => modules.find((module) => module.id === moduleKey);
}

/**
 * Where the application root goes. Without it the app's own URL — `…/preview` with nothing after it —
 * matches none of the children and the *whole* route fails to match, which would take the preview screen
 * down rather than merely leave its content area empty.
 *
 * The authored order decides, falling back to the first route as built. The fallback is not decoration: an
 * app may declare no routes of its own and mount all of its screens from a module, in which case the
 * definition offers no path to land on but the built routes do.
 */
function indexRoute(definition: AppDefinition, built: Routes) {
  const target = definition.routes?.find((route) => route.path)?.path ?? built[0].path;
  // `pathMatch: 'full'`, or an empty path would prefix-match every URL below it and swallow the app.
  return { path: '', pathMatch: 'full' as const, redirectTo: target };
}

/**
 * Registers an application's own routes as the children of the Preview tab's route, so that navigating
 * inside a previewed application is *real* navigation: real URLs, real back button, real outlet.
 *
 * Why a `canMatch` guard rather than `loadChildren`, which is the obvious hook for lazily-known children:
 * the router caches a `loadChildren` result on the `Route` object as `_loadedRoutes`, and one Route object
 * serves every `:entityId`. The first app previewed would therefore have its routes replayed for the
 * second. `children`, by contrast, is read fresh on every recognition and never cached — and `canMatch`
 * runs, and is awaited, *before* the router reads it. So this is the one hook that can answer "children of
 * *this* app" and still work on a deep link.
 *
 * Always returns `true`. It is not deciding whether the tab may be entered — it is filling in the tab's
 * children on the way past.
 */
export const appShellRoutesGuard: CanMatchFn = (route, _segments, snapshot) => {
  const appId = snapshot.paramMap.get(BaseUrlSegments.EntityID);
  if (!appId) return true;

  // `inject` before the first await: an async function loses the injection context at its first suspension.
  const factory = inject(AppShellRoutesFactory);
  return factory.childrenOf(appId).then((children) => {
    route.children = children;
    return true;
  });
};
