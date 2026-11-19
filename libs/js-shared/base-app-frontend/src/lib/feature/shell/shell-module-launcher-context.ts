import { computed, inject, Injectable } from '@angular/core';
import { ModuleLauncherContext, ModuleLauncherEntry } from '@processpuzzle/widgets';
import { catchError, map, Observable, of } from 'rxjs';
import { AppDefinition } from '../../domain/app-definition';
import { ModuleAssetService } from '../../domain/module-asset.service';
import { ModuleTranslationService } from '../../domain/module-translation.service';
import { ShellApplicationContext } from './shell-application-context';

/**
 * The shell's answer to base-widget's `MODULE_LAUNCHER_CONTEXT`: the mounted modules that brought a launcher card, and
 * the texts and pictures of each, for the `module-launcher` widget on the application's home page.
 *
 * Texts and pictures are read in the channel the shell's definition was built from — the released module in the running
 * application, the draft in the designer's Preview — the way the routes' own module contexts read theirs, so the launcher
 * never shows a text or picture its module has not released.
 *
 * Provided on `AppShellComponent`, next to {@link ShellApplicationContext}, whose definition it reads.
 */
@Injectable()
export class ShellModuleLauncherContext implements ModuleLauncherContext {
  private readonly application = inject(ShellApplicationContext);
  private readonly translations = inject(ModuleTranslationService);
  private readonly assets = inject(ModuleAssetService);

  readonly entries = computed(() => launcherEntriesOf(this.application.definition()));
  private readonly revision = computed(() => revisionOf(this.application.definition()));

  texts(moduleKey: string, lang: string): Observable<Record<string, string>> {
    return this.translations.bundle(moduleKey, lang, this.revision()).pipe(
      map((bundle) => bundle ?? {}),
      catchError(() => of({})),
    );
  }

  assetBlob(moduleKey: string, path: string): Observable<Blob> {
    return this.assets.content(moduleKey, path, this.revision());
  }
}

/** The running application's layout carries its released modules; the designer's own read does not, and is the draft. */
export function revisionOf(definition: AppDefinition | undefined): 'draft' | 'released' {
  return definition?.mountedModules ? 'released' : 'draft';
}

/**
 * One entry for each mount that has a launcher card, in mount order. A module's name is the card's title until a
 * translation replaces it; where the definition does not carry its modules (the designer's read) the key stands in.
 */
export function launcherEntriesOf(definition: AppDefinition | undefined): ModuleLauncherEntry[] {
  const names = new Map((definition?.mountedModules ?? []).map((module) => [module.id, module.name]));
  return (definition?.modules ?? []).flatMap((mount) => {
    const launcher = mount.launcher;
    if (!launcher?.titleKey) return [];
    return [
      {
        moduleKey: mount.moduleKey,
        name: names.get(mount.moduleKey) || mount.moduleKey,
        basePath: mount.basePath,
        ...(launcher.image ? { image: launcher.image } : {}),
        titleKey: launcher.titleKey,
        ...(launcher.subtitleKey ? { subtitleKey: launcher.subtitleKey } : {}),
        contentKeys: launcher.contentKeys ?? [],
        ...(launcher.link ? { link: launcher.link } : {}),
      },
    ];
  });
}
