import { Component, inject, output } from '@angular/core';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { provideTranslocoScope, TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { NgClass } from '@angular/common';
import { MatListOption, MatSelectionList, MatSelectionListChange } from '@angular/material/list';
import { LanguageConfig } from './language-config';

@Component({
  selector: 'pp-language-selector-list',
  template: `
    <ng-container *transloco="let t">
      <div class="language-selector">
        <mat-selection-list #selectionList (selectionChange)="onSelectionChange($event)" [multiple]="false">
          @for (language of languages; track language.code) {
            <mat-list-option
              [value]="language.code"
              [selected]="selectedLanguage === language.code"
              tabindex="0"
              role="option"
              [attr.aria-selected]="selectedLanguage === language.code ? 'true' : 'false'"
            >
              <span [ngClass]="language.flag">&nbsp;-&nbsp;</span>
              <span class="language-label">{{ t('widgets.' + language.label) }}</span>
            </mat-list-option>
          }
        </mat-selection-list>
      </div>
    </ng-container>
  `,
  styleUrls: ['language-selector-list.component.css'],
  imports: [NgClass, TranslocoDirective, MatListOption, MatSelectionList],
  providers: [provideTranslocoScope({ scope: 'widgets' })],
})
export class LanguageSelectorListComponent {
  private readonly translocoService = inject(TranslocoService);
  private readonly runtimeConfiguration = inject(RUNTIME_CONFIGURATION) as { LANGUAGE_CONFIGURATION: LanguageConfig };
  readonly languages = this.runtimeConfiguration.LANGUAGE_CONFIGURATION.AVAILABLE_LANGUAGES;
  languageSelected = output<void>();
  private _selectedLanguage?: string;

  constructor() {
    this._selectedLanguage = this.translocoService.getActiveLang();
  }

  get selectedLanguage(): string {
    return this._selectedLanguage ?? this.translocoService.getActiveLang();
  }

  // region event handling methods
  onSelectionChange(event: MatSelectionListChange) {
    const selectedOption = event.source;
    const selectedValue = selectedOption.selectedOptions.selected[0]?.value;

    if (this.selectedLanguage !== selectedValue) {
      this.translocoService.setActiveLang(selectedValue);
      this._selectedLanguage = selectedValue;
      this.languageSelected.emit();
    }
  }

  // endregion
}
