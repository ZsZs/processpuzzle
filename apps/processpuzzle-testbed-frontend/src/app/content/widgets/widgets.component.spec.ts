import { describe, expect, it } from 'vitest';
import { render } from '@testing-library/angular';
import '@testing-library/jest-dom/vitest';
import { provideRouter } from '@angular/router';
import { WidgetsComponent } from './widgets.component';

describe('WidgetsComponent', () => {
  it('renders overview and samples navigation tabs', async () => {
    const { getByRole } = await render(WidgetsComponent, {
      providers: [provideRouter([])],
    });

    expect(getByRole('tab', { name: 'Overview' })).toBeInTheDocument();
    expect(getByRole('tab', { name: 'Samples' })).toBeInTheDocument();
  });
});
