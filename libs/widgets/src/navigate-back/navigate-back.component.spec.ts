import { render, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { NavigateBackComponent } from './navigate-back.component';
import { NavigateBackService } from './navigate-back.service';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

// Create a mock NavigationBackService
const navigateBackServiceMock = {
  goBack: jest.fn(), // Mock the goBack method
};

describe('NavigateBackComponent', () => {
  beforeEach(async () => {
    await render(NavigateBackComponent, {
      imports: [MatIconModule, MatButtonModule], // Material modules used by the component
      providers: [
        {
          provide: NavigateBackService, // Provide the mock service
          useValue: navigateBackServiceMock,
        },
      ],
    });
  });

  it('should render the Go Back button with an icon', () => {
    // Verify that the button is in the document
    const button = screen.getByRole('button', { name: 'Go back' });
    expect(button).toBeInTheDocument();

    // Verify that the material icon is rendered
    const icon = screen.getByText('arrow_back');
    expect(icon).toBeInTheDocument();
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
