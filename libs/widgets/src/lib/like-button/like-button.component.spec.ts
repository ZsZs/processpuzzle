import { fireEvent, render, screen } from '@testing-library/angular';
import { LikeButtonComponent } from './like-button.component';
import { ApplicationPropertyStore } from '../app-property/app-property.store';
import { ApplicationProperty } from '../app-property/app-property';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar } from '@angular/material/snack-bar';

describe('LikeButtonComponent', () => {
  const properties: Array<ApplicationProperty> = [new ApplicationProperty('likes', '0')];
  const mockStore = {
    add: jest.fn(),
    entities: jest.fn().mockReturnValue(properties),
    error: jest.fn().mockReturnValue(undefined),
    update: jest.fn(),
    resetErrorState: jest.fn(),
  };

  const mockSnackBar = {
    open: jest.fn(),
  };

  it('should render initial likes count', async () => {
    await render(LikeButtonComponent, {
      componentProviders: [
        { provide: ApplicationPropertyStore, useValue: mockStore },
        { provide: MatSnackBar, useValue: mockSnackBar }
      ],
      imports: [MatIconModule, MatButtonModule],
    });

    expect(screen.getByText('0')).toBeTruthy(); // Verifies the initial likes count is rendered correctly
  });

  it('should increment likes count when like button is clicked', async () => {
    await render(LikeButtonComponent, {
      componentProviders: [
        { provide: ApplicationPropertyStore, useValue: mockStore },
        { provide: MatSnackBar, useValue: mockSnackBar }
      ],
      imports: [MatIconModule, MatButtonModule],
    });

    // Click the like button
    const likeButton = screen.getByRole('button', { name: /Like Button/i });
    fireEvent.click(likeButton);

    // Expect the mock update function to be called
    expect(mockStore.update).toHaveBeenCalledWith(expect.objectContaining({ name: 'likes', value: '1' }));

    // Verify that the count is updated in the DOM
    expect(screen.getByText('1')).toBeTruthy();
  });

  it('should add a new likes property if not found', async () => {
    // Modify the mock to return no entities initially
    mockStore.entities = jest.fn().mockReturnValue([]);

    await render(LikeButtonComponent, {
      componentProviders: [
        { provide: ApplicationPropertyStore, useValue: mockStore },
        { provide: MatSnackBar, useValue: mockSnackBar }
      ],
      imports: [MatIconModule, MatButtonModule],
    });

    // Click the like button
    const likeButton = screen.getByRole('button', { name: /Like Button/i });
    fireEvent.click(likeButton);

    // Expect the add method to be called with a new ApplicationProperty
    expect(mockStore.add).toHaveBeenCalledWith(expect.objectContaining({ name: 'likes', value: '1' }));
  });

  it('should display error message when store has an error', async () => {
    // Set up the error in the store
    const errorMessage = 'Failed to update likes';
    mockStore.error = jest.fn().mockReturnValue(errorMessage);

    await render(LikeButtonComponent, {
      componentProviders: [
        { provide: ApplicationPropertyStore, useValue: mockStore },
        { provide: MatSnackBar, useValue: mockSnackBar }
      ],
      imports: [MatIconModule, MatButtonModule],
    });

    // Verify that the snackBar.open was called with the error message
    expect(mockSnackBar.open).toHaveBeenCalledWith(errorMessage, 'Close', expect.any(Object));

    // Verify that resetErrorState was called
    expect(mockStore.resetErrorState).toHaveBeenCalled();
  });
});
