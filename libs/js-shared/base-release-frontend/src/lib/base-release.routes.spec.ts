import { describe, expect, it } from 'vitest';
import { BASE_RELEASE_ROUTES } from './base-release.routes';
import { ReleasePanelComponent } from './release/release-panel.component';

describe('BASE_RELEASE_ROUTES', () => {
  it('mounts the Release section, titled from the design scope like every designer section', () => {
    expect(BASE_RELEASE_ROUTES).toHaveLength(1);
    const [release] = BASE_RELEASE_ROUTES;

    expect(release.path).toBe('release');
    expect(release.component).toBe(ReleasePanelComponent);
    expect(release.data).toEqual({ icon: 'rocket_launch', menuTitle: 'design.release' });
  });
});
