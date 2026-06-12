import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { NavigateBackComponent } from './navigate-back.component';
import { NavigateBackService } from './navigate-back.service';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { TestBed } from '@angular/core/testing';

// Create a mock NavigationBackService
const navigateBackServiceMock = {
  goBack: vi.fn(), // Mock the goBack method
};

describe.skip('NavigateBackComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MatIconModule, MatButtonModule, NavigateBackComponent],
      providers: [{ provide: NavigateBackService, useValue: navigateBackServiceMock }],
    }).compileComponents();
  });

  it('should render the Go Back button with an icon', () => {
    // Verify that the button is in the document
    const button = screen.getByRole('button', { name: 'Go back' });
    expect(button).toBeTruthy();

    // Verify that the material icon is rendered
    const icon = screen.getByText('arrow_back');
    expect(icon).toBeTruthy();
    expect(icon).toHaveClass('fat-back-arrow'); // Verify the custom class
  });

  it('should call the NavigateBackService goBack method when the button is clicked', async () => {
    // Find the button
    const button = screen.getByRole('button', { name: 'Go back' });

    // Simulate a user clicking the button using userEvent
    const user = userEvent.setup();
    await user.click(button);

    // Assert that the goBack method was called
    expect(navigateBackServiceMock.goBack).toHaveBeenCalledTimes(1);
  });
});
