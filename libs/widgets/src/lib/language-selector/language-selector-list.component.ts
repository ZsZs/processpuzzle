import { Component, inject } from '@angular/core';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { LanguageConfig } from './language-config';
import { provideTranslocoScope, TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { NgClass, NgForOf } from '@angular/common';
import { MatListOption, MatSelectionList } from '@angular/material/list';

@Component({
  selector: 'pp-language-selector-list',
  template: `
    <ng-container *transloco="let t; prefix: 'language-selector'">
      <div class="language-selector">
        <mat-selection-list #selectionList (selectionChange)="onSelectionChange($event)" [multiple]="false">
          <mat-list-option
            *ngFor="let language of languages"
            [value]="language.code"
            [selected]="selectedLanguage === language.code"
            tabindex="0"
            role="option"
            [attr.aria-selected]="selectedLanguage === language.code ? 'true' : 'false'"
          >
            <span [ngClass]="language.flag">&nbsp;-&nbsp;</span>
            <span>{{ t(language.label) }}</span>
          </mat-list-option>
        </mat-selection-list>
      </div>
    </ng-container>
  `,
  styleUrls: ['language-selector-list.component.css'],
  imports: [NgClass, NgForOf, TranslocoDirective, MatListOption, MatSelectionList],
  providers: [provideTranslocoScope('widgets')],
})
export class LanguageSelectorListComponent {
  private readonly translocoService = inject(TranslocoService);
  private readonly runtimeConfiguration: LanguageConfig = inject(RUNTIME_CONFIGURATION);
  readonly languages = this.runtimeConfiguration.AVAILABLE_LANGUAGES;
  selectedLanguage = this.runtimeConfiguration.DEFAULT_LANGUAGE;

  // region event handling methods
  onSelectionChange(event: any) {
    const selectedOption = event.source;
    const selectedValue = selectedOption.selectedOptions.selected[0]?.value;

    if (this.selectedLanguage !== selectedValue) {
      this.translocoService.setActiveLang(selectedValue);
      this.selectedLanguage = selectedValue;
    }
  }

  // endregion
}
