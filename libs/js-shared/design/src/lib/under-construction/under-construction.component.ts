import { Component } from '@angular/core';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'pp-under-construction',
  standalone: true,
  imports: [TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'design' })],
  template: `
    <div class="under-construction" *transloco="let t; prefix: 'design'">
      <span class="material-symbols-outlined icon">construction</span>
      <h2>{{ t('under-construction_title') }}</h2>
      <p>{{ t('under-construction_message') }}</p>
    </div>
  `,
  styles: [
    `
      .under-construction {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 8px;
        padding: 48px 16px;
        text-align: center;
      }
      .under-construction .icon {
        font-size: 64px;
      }
    `,
  ],
})
export class UnderConstructionComponent {}
