import { Component, inject, output } from '@angular/core';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { LanguageConfig } from './language-config';
import { provideTranslocoScope, TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { NgClass } from '@angular/common';
import { MatListOption, MatSelectionList, MatSelectionListChange } from '@angular/material/list';

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
              <span>{{ t(language.label) }}</span>
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
  private readonly runtimeConfiguration: LanguageConfig = inject(RUNTIME_CONFIGURATION);
  readonly languages = this.runtimeConfiguration.AVAILABLE_LANGUAGES;
  languageSelected = output<void>();
  private _selectedLanguage?: string;

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
