import { render, screen } from '@testing-library/angular';
import { VersionButtonComponent } from './version-button.component';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { describe, expect, it } from 'vitest';

describe('VersionButtonComponent', () => {
  it('should render the application version from runtime configuration', async () => {
    await render(VersionButtonComponent, {
      providers: [{ provide: RUNTIME_CONFIGURATION, useValue: { BASE_CONFIGURATION: { APPLICATION_VERSION: '1.4.0' } } }],
    });

    expect(screen.getByText('v1.4.0')).toBeTruthy();
  });
});
