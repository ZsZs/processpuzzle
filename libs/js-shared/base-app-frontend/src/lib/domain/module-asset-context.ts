import { ModuleAssetContext } from '@processpuzzle/widgets';
import { ModuleAssetService } from './module-asset.service';

/**
 * The {@link ModuleAssetContext} of one mounted module: what its widgets ask when a property says
 * `module:<path>`. Reads the channel the mount itself was built from — the draft in the designer's Preview, the
 * released module in the running application — so a widget never shows an asset its module has not released.
 */
export function moduleAssetContextOf(service: ModuleAssetService, moduleKey: string, revision: 'draft' | 'released'): ModuleAssetContext {
  return { blob: (path) => service.content(moduleKey, path, revision) };
}
