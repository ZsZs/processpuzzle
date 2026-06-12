import { Component } from '@angular/core';
import { translateSignal, TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'transloco-test',
  standalone: true,
  template: `
    <div>
      <h1>Transloco Signal Test Component</h1>
      <p>{{ ok() }}</p>
      <p>{{ prefixedOk() }}</p>
    </div>
    <ng-container *transloco="let t; scope: 'widgets'">
      <div>
        <h1>Transloco Directive Test Component</h1>
        <p>{{ t('widgets.PREFIXED.OK') }}</p>
        <p>{{ t('widgets.PREFIXED.TEST') }}</p>
      </div>
    </ng-container>
  `,
  providers: [],
  imports: [TranslocoDirective],
})
export class TranslocoTestComponent {
  ok = translateSignal('OK', {}, { scope: 'widgets' });
  prefixedOk = translateSignal('TEST', {}, { scope: 'widgets' });
}
