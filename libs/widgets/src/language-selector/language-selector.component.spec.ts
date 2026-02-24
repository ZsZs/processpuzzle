import '../../test-setup';
import { fireEvent, screen, waitFor } from '@testing-library/angular';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { LanguageSelectorComponent } from './language-selector.component';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { OverlayModule } from '@angular/cdk/overlay';

describe('LanguageSelectorComponent', () => {
  const testConfig: TranslocoTestConfig = {
    scope: 'widgets',
    translations: {
      en: { widgets: { english: 'English', spanish: 'Spanish', german: 'German' } },
      es: { widgets: { english: 'English', spanish: 'Spanish', german: 'German' } },
      de: { widgets: { english: 'English', spanish: 'Spanish', german: 'German' } },
    },
  };

  beforeEach(async () => {
    await setUpTranslocoTestBed(LanguageSelectorComponent, testConfig, {
      imports: [MatIconModule, MatButtonModule, OverlayModule],
      providers: [{ provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig }],
    });
  });

  it('should render the component', async () => {
    expect(screen.getByRole('button', { name: /select language button/i })).toBeVisible();
  });

  it('should open the overlay when the button is clicked', async () => {
    // Initially, overlay content should not be in the DOM
    expect(screen.queryByRole('dialog')).toBeNull();

    // Click the button to open the overlay
    const button = screen.getByRole('button', { name: /select language button/i });
    fireEvent.click(button);

    // Verify if content is displayed after clicking
    await waitFor(() => {
      expect(screen.getByText('English')).toBeVisible();
      expect(screen.getByText('German')).toBeVisible();
      expect(screen.getByText('Spanish')).toBeVisible();
    });
  });

  it('should close the overlay on backdrop click', async () => {
    // Open the overlay by clicking the button
    const button = screen.getByRole('button', { name: /select language button/i });
    fireEvent.click(button);

    // Wait for the overlay to appear
    await waitFor(() => {
      expect(screen.getByText('English')).toBeVisible();
      expect(screen.getByText('German')).toBeVisible();
      expect(screen.getByText('Spanish')).toBeVisible();
    });

    // Close the overlay by simulating a backdrop click
    const backdrop = document.querySelector('.cdk-overlay-backdrop') as HTMLElement;
    fireEvent.click(backdrop);

    // After the backdrop click, verify the content is removed
    await waitFor(() => {
      expect(screen.queryByText('Language Selector')).toBeNull();
    });
  });
});
