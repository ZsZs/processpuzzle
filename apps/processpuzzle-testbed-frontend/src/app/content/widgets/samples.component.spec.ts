import { fireEvent, render } from '@testing-library/angular';
import '@testing-library/jest-dom/vitest';
import { describe, expect, it, vi } from 'vitest';
import { LayoutService } from '@processpuzzle/util';
import { provideTranslocoTesting } from '@processpuzzle/test-util';
import { WidgetsSamplesComponent } from './samples.component';

describe('WidgetsSamplesComponent', () => {
  it('shows the copyright sample and documents its required text input', async () => {
    const { getByRole, getByText } = await render(WidgetsSamplesComponent, {
      providers: [{ provide: LayoutService, useValue: { hideSidenav: vi.fn() } }, provideTranslocoTesting({ translations: {} })],
    });

    expect(getByText('© Zsolt Zsuffa 2026')).toBeTruthy();
    expect(getByRole('cell', { name: 'text' })).toBeTruthy();
    expect(getByRole('cell', { name: 'string' })).toBeTruthy();
    expect(getByRole('cell', { name: 'Yes' })).toBeTruthy();
    expect(getByRole('cell', { name: 'The copyright notice to display.' })).toBeTruthy();
  });

  it('shows the image zoom sample and opens its image at full size', async () => {
    const restoreSidenav = vi.fn();
    const hideSidenav = vi.fn(() => restoreSidenav);
    const { getAllByRole, getByRole } = await render(WidgetsSamplesComponent, {
      providers: [{ provide: LayoutService, useValue: { hideSidenav } }, provideTranslocoTesting({ translations: {} })],
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

  it('shows one photo album image at a time and opens the selected image at full size', async () => {
    const restoreSidenav = vi.fn();
    const hideSidenav = vi.fn(() => restoreSidenav);
    const { getAllByRole, getByRole, queryAllByRole } = await render(WidgetsSamplesComponent, {
      providers: [{ provide: LayoutService, useValue: { hideSidenav } }, provideTranslocoTesting({ translations: {} })],
    });
    const analyseRace = getByRole('img', { name: 'Analyse Race diagram' });

    expect(analyseRace).toHaveAttribute('src', 'assets/Analyse_Race.png');
    expect(queryAllByRole('img', { name: 'Plan Race diagram' })).toHaveLength(0);

    fireEvent.click(getByRole('button', { name: 'Next image' }));

    const [, conductRace] = getAllByRole('img', { name: 'Conduct Race diagram' });
    expect(conductRace).toHaveAttribute('src', 'assets/Conduct_Race.png');

    fireEvent.click(getByRole('button', { name: 'Next image' }));

    const planRace = getByRole('img', { name: 'Plan Race diagram' });
    expect(planRace).toHaveAttribute('src', 'assets/Plan_Race.png');

    fireEvent.click(planRace);

    const [, fullSizeImage] = getAllByRole('img', { name: 'Plan Race diagram' });
    expect(fullSizeImage).toHaveClass('full');
    expect(hideSidenav).toHaveBeenCalledOnce();

    fireEvent.click(getByRole('button', { name: 'Close' }));

    expect(restoreSidenav).toHaveBeenCalledOnce();
  });

  it('shows the themes button sample, themed on its own box rather than the page', async () => {
    const { container, getByRole } = await render(WidgetsSamplesComponent, {
      providers: [{ provide: LayoutService, useValue: { hideSidenav: vi.fn() } }, provideTranslocoTesting({ translations: {} })],
    });

    expect(container.querySelector('app-themes-button-sample')).toHaveClass('pp-theme-processpuzzle', 'pp-scheme-light');
    expect(document.documentElement.className).not.toContain('pp-theme-');
    expect(getByRole('cell', { name: 'themeChange' })).toBeTruthy();
  });
});
