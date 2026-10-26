import { render } from '@testing-library/angular';
import '@testing-library/jest-dom/vitest';
import { LayoutService } from '@processpuzzle/util';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { MODULE_ASSET_CONTEXT } from '../module-asset/module-asset-context';
import { ImageZoomComponent } from './image-zoom.component';

describe('ImageZoomComponent', () => {
  const layout = { provide: LayoutService, useValue: { hideSidenav: vi.fn() } };

  it('shows an ordinary URL as it is', async () => {
    const { getByRole } = await render(ImageZoomComponent, { inputs: { src: 'assets/a.png', alt: 'A' }, providers: [layout] });

    expect(getByRole('img', { name: 'A' })).toHaveAttribute('src', 'assets/a.png');
  });

  it('shows an image of the module from an object URL, since an <img> cannot send the bearer token', async () => {
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:module-image'), revokeObjectURL: vi.fn() });
    const blob = vi.fn(() => of(new Blob(['x'])));

    const { getByRole } = await render(ImageZoomComponent, {
      inputs: { src: 'module:img/a.png', alt: 'A' },
      providers: [layout, { provide: MODULE_ASSET_CONTEXT, useValue: { blob } }],
    });

    expect(blob).toHaveBeenCalledWith('img/a.png');
    expect(getByRole('img', { name: 'A' })).toHaveAttribute('src', 'blob:module-image');
  });
});
