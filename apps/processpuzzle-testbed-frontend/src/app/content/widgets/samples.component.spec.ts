import { fireEvent, render } from '@testing-library/angular';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { LayoutService } from '@processpuzzle/util';
import { WidgetsSamplesComponent } from './samples.component';

describe('WidgetsSamplesComponent', () => {
  it('shows the Conduct Race image and opens it at full size when clicked', async () => {
    const restoreSidenav = vi.fn();
    const hideSidenav = vi.fn(() => restoreSidenav);
    const { getAllByRole, getByRole } = await render(WidgetsSamplesComponent, {
      providers: [{ provide: LayoutService, useValue: { hideSidenav } }],
    });
    const [thumbnail] = getAllByRole('img', { name: 'Conduct Race diagram' });

    expect(thumbnail).toHaveAttribute('src', 'assets/Conduct_Race.png');
    expect(thumbnail.closest('app-image-zoom')).toHaveClass('image-zoom-sample');

    fireEvent.click(thumbnail);

    const [, fullSizeImage] = getAllByRole('img', { name: 'Conduct Race diagram' });
    expect(fullSizeImage).toHaveClass('full');
    expect(hideSidenav).toHaveBeenCalledOnce();

    fireEvent.click(getByRole('button', { name: 'Close' }));

    expect(restoreSidenav).toHaveBeenCalledOnce();
  });
});
