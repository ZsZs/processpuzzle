import { AppAssetContext } from '@processpuzzle/widgets';
import { AppAssetService } from './app-asset.service';

/**
 * The {@link AppAssetContext} of one application: what its widgets ask when a property says `app:<path>`. Reads the
 * channel the routes were built from — the draft in the designer's Preview, the released one in the running
 * application — so a widget never shows an asset its application has not released.
 */
export function appAssetContextOf(service: AppAssetService, appId: string, revision: 'draft' | 'released'): AppAssetContext {
  return { blob: (path) => service.content(appId, path, revision) };
}
