import { TranslocoModule, TranslocoService } from '@jsverse/transloco';
import { NgModule } from '@angular/core';
import de from './assets/i18n/widgets/de.json';
import hu from './assets/i18n/widgets/hu.json';
import en from './assets/i18n/widgets/en.json';
import es from './assets/i18n/widgets/es.json';

@NgModule({
  imports: [TranslocoModule],
  exports: [TranslocoModule],
  providers: [],
})
export class WidgetsModule {
  constructor(private _translate: TranslocoService) {
    this._translate.setTranslation({ widgets: de }, 'de');
    this._translate.setTranslation({ widgets: hu }, 'hu');
    this._translate.setTranslation({ widgets: en }, 'en');
    this._translate.setTranslation({ widgets: es }, 'es');
  }
}
