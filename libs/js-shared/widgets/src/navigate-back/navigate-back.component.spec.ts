import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { NavigateBackComponent } from './navigate-back.component';
import { NavigateBackService } from './navigate-back.service';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

// Mock NavigateBackService so the component under test carries no navigation dependencies.
const navigateBackServiceMock = {
  goBack: vi.fn(),
};

async function setup() {
  return render(NavigateBackComponent, {
    imports: [MatIconModule, MatButtonModule],
    componentProviders: [{ provide: NavigateBackService, useValue: navigateBackServiceMock }],
  });
}

describe('NavigateBackComponent', () => {
  beforeEach(() => {
    navigateBackServiceMock.goBack.mockReset();
  });

  it('should render the Go back button with the arrow icon', async () => {
    await setup();

    expect(screen.getByRole('button', { name: 'Go back' })).toBeTruthy();
    const icon = screen.getByText('arrow_back');
    expect(icon).toBeTruthy();
    expect(icon).toHaveClass('fat-back-arrow');
  });

  it('should call NavigateBackService.goBack when the button is clicked', async () => {
    await setup();

    await userEvent.click(screen.getByRole('button', { name: 'Go back' }));

    expect(navigateBackServiceMock.goBack).toHaveBeenCalledTimes(1);
  });
});
