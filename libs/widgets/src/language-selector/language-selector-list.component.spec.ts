import '../../test-setup';
import { fireEvent, screen } from '@testing-library/angular';
import userEvent from '@testing-library/user-event';
import { LanguageSelectorListComponent } from './language-selector-list.component';
import { TranslocoService } from '@jsverse/transloco';
import { mockLanguageConfig, setUpTranslocoTestBed, TranslocoTestConfig } from '@processpuzzle/test-util';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';

describe('LanguageSelectorListComponent', () => {
  const testConfig: TranslocoTestConfig = {
    scope: 'widgets',
    translations: {
      en: { widgets: { english: 'English', spanish: 'Spanish', german: 'German' } },
      es: { widgets: { english: 'English', spanish: 'Spanish', german: 'German' } },
      de: { widgets: { english: 'English', spanish: 'Spanish', german: 'German' } },
    },
  };
  let translocoService: TranslocoService;

  beforeEach(async () => {
    const result = await setUpTranslocoTestBed(LanguageSelectorListComponent, testConfig, {
      providers: [{ provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig }],
    });
    translocoService = result.translocoService;
  });

  it('should render the list of languages correctly', () => {
    expect(screen.getByText('English')).toBeInTheDocument();
    expect(screen.getByText('Spanish')).toBeInTheDocument();
    expect(screen.getByText('German')).toBeInTheDocument();
  });

  it('should properly highlight the default selected language', () => {
    const englishOption = screen.getByRole('option', { name: /English/ });
    expect(englishOption).toHaveAttribute('aria-selected', 'true');
  });

  it('should call onLanguageChange when a language is clicked', async () => {
    const spy = vi.spyOn(translocoService, 'setActiveLang');
    const spanishOption = screen.getByRole('option', { name: /Spanish/ });
    await userEvent.click(spanishOption);
    expect(spy).toHaveBeenCalledWith('es');
  });

  it('should not do anything if a non-interactive key is pressed', () => {
    const spy = vi.spyOn(translocoService, 'setActiveLang');
    const spanishOption = screen.getByRole('option', { name: /Spanish/ });
    fireEvent.keyDown(spanishOption, { code: 'ArrowDown', key: 'ArrowDown' });
    expect(spy).not.toHaveBeenCalled();
  });
});
