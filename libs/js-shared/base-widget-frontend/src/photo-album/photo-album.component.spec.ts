import { fireEvent, render } from '@testing-library/angular';
import '@testing-library/jest-dom/vitest';
import { LayoutService } from '@processpuzzle/util';
import { of } from 'rxjs';
import { MODULE_ASSET_CONTEXT } from '../module-asset/module-asset-context';
import { describe, expect, it, vi } from 'vitest';
import { PhotoAlbumComponent } from './photo-album.component';

describe('PhotoAlbumComponent', () => {
  const images = [
    { src: 'assets/Analyse_Race.png', alt: 'Analyse Race diagram' },
    { src: 'assets/Conduct_Race.png', alt: 'Conduct Race diagram' },
  ];

  it('shows one embedded image and switches it with the overlaid controls', async () => {
    const { getAllByRole, getByRole } = await render(PhotoAlbumComponent, {
      inputs: { images, maxHeight: 240 },
      providers: [{ provide: LayoutService, useValue: { hideSidenav: vi.fn() } }],
    });

    expect(getAllByRole('img')).toHaveLength(1);
    expect(getByRole('img', { name: 'Analyse Race diagram' })).toHaveAttribute('src', 'assets/Analyse_Race.png');

    fireEvent.click(getByRole('button', { name: 'Next image' }));

    expect(getByRole('img', { name: 'Conduct Race diagram' })).toHaveAttribute('src', 'assets/Conduct_Race.png');
    expect(getByRole('button', { name: 'Previous image' })).toBeEnabled();
  });

  it('shows an image of the module from an object URL', async () => {
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:module-image'), revokeObjectURL: vi.fn() });
    const blob = vi.fn(() => of(new Blob(['x'])));

    const { getByRole } = await render(PhotoAlbumComponent, {
      inputs: { images: [{ src: 'module:img/a.png', alt: 'From the module' }], maxHeight: 240 },
      providers: [
        { provide: LayoutService, useValue: { hideSidenav: vi.fn() } },
        { provide: MODULE_ASSET_CONTEXT, useValue: { blob } },
      ],
    });

    expect(blob).toHaveBeenCalledWith('img/a.png');
    expect(getByRole('img', { name: 'From the module' })).toHaveAttribute('src', 'blob:module-image');
  });

  it('navigates images in the viewer and restores the sidebar after closing', async () => {
    const restoreSidenav = vi.fn();
    const hideSidenav = vi.fn(() => restoreSidenav);
    const { getAllByRole, getByRole } = await render(PhotoAlbumComponent, {
      inputs: { images, maxHeight: '240px' },
      providers: [{ provide: LayoutService, useValue: { hideSidenav } }],
    });

    fireEvent.click(getByRole('img', { name: 'Analyse Race diagram' }));

    const [, fullSizeImage] = getAllByRole('img', { name: 'Analyse Race diagram' });
    expect(fullSizeImage).toHaveClass('full');
    expect(hideSidenav).toHaveBeenCalledOnce();

    fireEvent.click(getByRole('button', { name: 'Next image in viewer' }));

    const [, nextFullSizeImage] = getAllByRole('img', { name: 'Conduct Race diagram' });
    expect(nextFullSizeImage).toHaveClass('full');

    fireEvent.click(getByRole('button', { name: 'Close' }));

    expect(restoreSidenav).toHaveBeenCalledOnce();
  });
});
