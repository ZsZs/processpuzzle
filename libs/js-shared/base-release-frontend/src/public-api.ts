/*
 * Public API Surface of @processpuzzle/base-release
 */
export { BASE_RELEASE_TRANSLATION_SOURCE, BASE_RELEASE_TRANSLOCO_SCOPE } from './lib/base-release.i18n';
export { BASE_RELEASE_ROUTES } from './lib/base-release.routes';
export { ReleasePanelComponent } from './lib/release/release-panel.component';
export { ReleaseService } from './lib/release/release.service';
export type { PublishOutcome } from './lib/release/release.service';
export type { PendingChange, PendingRelease, Release, ReleaseItem, ReleaseProblem } from './lib/release/release';
