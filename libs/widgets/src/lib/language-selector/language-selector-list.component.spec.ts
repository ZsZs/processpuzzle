import { fireEvent, screen } from '@testing-library/angular';
import { LanguageSelectorListComponent } from './language-selector-list.component';
import { NgClass, NgForOf } from '@angular/common';
import userEvent from '@testing-library/user-event';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { getTranslocoModule } from './transloco-testing.module';
import { translate, TranslocoService } from '@jsverse/transloco';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { MatListOption, MatSelectionList } from '@angular/material/list';

describe('LanguageSelectorListComponent', () => {
  let fixture: ComponentFixture<LanguageSelectorListComponent>;
  let translocoService: TranslocoService;

  const mockTranslocoService = {
    load: jest.fn(),
    setActiveLang: jest.fn(),
  };
  const mockLanguageConfig = {
    AVAILABLE_LANGUAGES: [
      { code: 'en', flag: 'flag-en', label: 'english' },
      { code: 'es', flag: 'flag-es', label: 'spanish' },
      { code: 'de', flag: 'flag-de', label: 'german' },
    ],
    DEFAULT_LANGUAGE: 'en',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [getTranslocoModule(), LanguageSelectorListComponent, MatListOption, MatSelectionList, NgClass, NgForOf],
      providers: [TranslocoService, { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig }],
    }).compileComponents();

    fixture = TestBed.createComponent(LanguageSelectorListComponent);
    translocoService = TestBed.inject(TranslocoService);
    fixture.detectChanges();
  });

  it('should render the list of languages correctly', () => {
    mockLanguageConfig.AVAILABLE_LANGUAGES?.forEach((language) => {
      const text = translate('language-selector.' + language.label);
      expect(screen.getByText(text)).toBeInTheDocument();
    });
  });

  it('should properly highlight the default selected language', async () => {
    const activeElement = screen.getByText('English');
    expect(activeElement.parentElement?.parentElement?.parentElement).toHaveAttribute('aria-selected', 'true');
  });

  it('should call onLanguageChange when a language is clicked', async () => {
    const spy = jest.spyOn(translocoService, 'setActiveLang');
    const spanishItem = screen.getByText('Spanish');
    await userEvent.click(spanishItem);
    expect(spy).toHaveBeenCalledWith('es');
    expect(spanishItem.parentElement?.parentElement?.parentElement).toHaveAttribute('aria-selected', 'true');
  });

  it('should not do anything if a non-interactive key is pressed', async () => {
    const spanishItem = screen.getByText('Spanish');
    fireEvent.keyDown(spanishItem, { code: 'ArrowDown', key: 'ArrowDown' });
    expect(mockTranslocoService.setActiveLang).not.toHaveBeenCalled();
  });
});
