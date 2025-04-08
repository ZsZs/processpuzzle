import { fireEvent, render, screen } from '@testing-library/angular';
import { LikeButtonComponent } from './like-button.component';
import { ApplicationPropertyStore } from '../app-property/app-property.store';
import { ApplicationProperty } from '../app-property/app-property';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';

describe('LikeButtonComponent', () => {
  const properties: Array<ApplicationProperty> = [new ApplicationProperty('likes', '0')];
  const mockStore = {
    entities: jest.fn().mockReturnValue(properties),
    update: jest.fn(),
    add: jest.fn(),
  };
  beforeEach(() => {
    console.log(mockStore.entities());
  });

  it('should render initial likes count', async () => {
    await render(LikeButtonComponent, {
      componentProviders: [{ provide: ApplicationPropertyStore, useValue: mockStore }],
      imports: [MatIconModule, MatButtonModule],
    });

    expect(screen.getByText('0')).toBeTruthy(); // Verifies the initial likes count is rendered correctly
  });

  it('should increment likes count when like button is clicked', async () => {
    await render(LikeButtonComponent, {
      componentProviders: [{ provide: ApplicationPropertyStore, useValue: mockStore }],
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
      componentProviders: [{ provide: ApplicationPropertyStore, useValue: mockStore }],
      imports: [MatIconModule, MatButtonModule],
    });

    // Click the like button
    const likeButton = screen.getByRole('button', { name: /Like Button/i });
    fireEvent.click(likeButton);

    // Expect the add method to be called with a new ApplicationProperty
    expect(mockStore.add).toHaveBeenCalledWith(expect.objectContaining({ name: 'likes', value: '1' }));
  });
});
