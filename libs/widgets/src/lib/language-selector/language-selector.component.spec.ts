import { fireEvent, render, screen } from '@testing-library/angular';
import { LanguageSelectorComponent } from './language-selector.component';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { OverlayModule } from '@angular/cdk/overlay';
import { LanguageSelectorListComponent } from './language-selector-list.component';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

describe('LanguageSelectorComponent', () => {
  const mockLanguageConfig = {
    AVAILABLE_LANGUAGES: [
      { code: 'en', flag: 'flag-en', label: 'english' },
      { code: 'es', flag: 'flag-es', label: 'spanish' },
      { code: 'de', flag: 'flag-de', label: 'german' },
    ],
    DEFAULT_LANGUAGE: 'en',
  };

  async function setup() {
    return render(LanguageSelectorComponent, {
      declarations: [LanguageSelectorListComponent],
      imports: [
        TranslocoTestingModule.forRoot({
          langs: {
            en: { 'language-selector': { title: 'Language Selector' } },
          },
        }),
        MatIconModule,
        MatButtonModule,
        OverlayModule,
      ],
      providers: [{ provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig }],
    });
  }

  it('should render the component', async () => {
    await setup();

    expect(screen.getByRole('button', { name: /select language button/i })).toBeVisible();
  });

  it('should open the overlay when the button is clicked', async () => {
    await setup();

    // Initially, overlay content should not be in the DOM
    expect(screen.queryByRole('dialog')).toBeNull();

    // Click the button to open the overlay
    const button = screen.getByRole('button', { name: /select language button/i });
    fireEvent.click(button);

    // Verify if content is displayed after clicking
    expect(screen.getByText('en.language-selector.spanish')).toBeVisible();
  });

  it('should close the overlay on backdrop click', async () => {
    await setup();

    // Open the overlay by clicking the button
    const button = screen.getByRole('button', { name: /select language button/i });
    fireEvent.click(button);

    // Verify if the overlay is visible
    expect(screen.getByText('en.language-selector.spanish')).toBeVisible();

    // Close the overlay by simulating a backdrop click
    const backdrop = document.querySelector('.cdk-overlay-backdrop') as HTMLElement;
    fireEvent.click(backdrop);

    // After the backdrop click, verify the content is removed
    expect(screen.queryByText('Language Selector')).toBeNull();
  });
});
