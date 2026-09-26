import { render } from '@testing-library/angular';
import { describe, expect, it } from 'vitest';
import { CopyrightComponent } from './copyright.component';

describe('CopyrightComponent', () => {
  it('renders the configured copyright text', async () => {
    const { getByText } = await render(CopyrightComponent, {
      inputs: { text: 'Zsolt Zsuffa 2026' },
    });

    expect(getByText('© Zsolt Zsuffa 2026')).toBeTruthy();
  });
});
