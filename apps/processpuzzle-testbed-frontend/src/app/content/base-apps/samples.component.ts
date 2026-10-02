import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { AppConceptsGuideComponent } from '@processpuzzle/base-app';

@Component({
  selector: 'base-apps-samples',
  standalone: true,
  imports: [AppConceptsGuideComponent, RouterOutlet, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'base_app', alias: 'base_app' })],
  template: `
    <ng-container *transloco="let t; prefix: 'base-apps'">
      <p>{{ t('samples_desc_1') }}</p>
      <p><strong>{{ t('samples_desc_2') }}</strong></p>
    </ng-container>
    <router-outlet />
    <pp-app-concepts-guide />
  `,
})
export class SamplesComponent {}
