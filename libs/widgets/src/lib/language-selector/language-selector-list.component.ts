import { Component, inject } from '@angular/core';
import { RUNTIME_CONFIGURATION } from '@processpuzzle/util';
import { LanguageConfig } from './language-config';
import { TranslocoHttpLoader } from './transloco.loader';
import { TRANSLOCO_LOADER, TranslocoService } from '@jsverse/transloco';
import { NgClass, NgForOf } from '@angular/common';

@Component({
  selector: 'pp-language-selector-list',
  template: `
    <div class="language-selector">
      <ul>
        <li *ngFor="let language of languages" (click)="onLanguageChange(language.code)" [class.active]="selectedLanguage === language.code">
          <span [ngClass]="language.flag">&nbsp;-&nbsp;</span>
          <span>{{ language.label }}</span>
        </li>
      </ul>
    </div>
  `,
  styleUrls: ['language-selector-list.component.css'],
  imports: [NgClass, NgForOf],
  providers: [{ provide: TRANSLOCO_LOADER, useClass: TranslocoHttpLoader }],
})
export class LanguageSelectorListComponent {
  private readonly translocoService = inject(TranslocoService);
  private readonly runtimeConfiguration: LanguageConfig = inject(RUNTIME_CONFIGURATION);
  readonly languages = this.runtimeConfiguration.AVAILABLE_LANGUAGES;
  selectedLanguage = this.runtimeConfiguration.DEFAULT_LANGUAGE;

  // region event handling methods
  onLanguageChange(languageCode: string) {
    this.translocoService.setActiveLang(languageCode);
    this.selectedLanguage = languageCode;
  }

  // endregion
}
