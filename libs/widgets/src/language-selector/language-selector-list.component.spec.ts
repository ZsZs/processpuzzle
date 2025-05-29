import { fireEvent, screen } from '@testing-library/angular';
import { LanguageSelectorListComponent } from './language-selector-list.component';
import { NgClass, NgForOf } from '@angular/common';
import userEvent from '@testing-library/user-event';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { getTranslocoTestingModule, mockLanguageConfig, mockTranslocoService } from '@processpuzzle/test-util';
import { translate, TranslocoService } from '@jsverse/transloco';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { MatListOption, MatSelectionList } from '@angular/material/list';
import widgetsDe from '../assets/i18n/widgets/de.json';
import widgetsEn from '../assets/i18n/widgets/en.json';

describe('LanguageSelectorListComponent', () => {
  let fixture: ComponentFixture<LanguageSelectorListComponent>;
  let translocoService: TranslocoService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [getTranslocoTestingModule({ 'widgets/de': widgetsDe, 'widgets/en': widgetsEn }), LanguageSelectorListComponent, MatListOption, MatSelectionList, NgClass, NgForOf],
      providers: [TranslocoService, { provide: RUNTIME_CONFIGURATION, useValue: mockLanguageConfig }],
    }).compileComponents();

    fixture = TestBed.createComponent(LanguageSelectorListComponent);
    translocoService = TestBed.inject(TranslocoService);
    fixture.detectChanges();
  });

  it('should render the list of languages correctly', () => {
    mockLanguageConfig.AVAILABLE_LANGUAGES?.forEach((language) => {
      const text = translate('widgets.' + language.label);
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
