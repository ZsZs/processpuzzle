import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'transloco-test',
  template: `
    <div>
      <h1>Transloco Test Component</h1>
      <p>{{ 'TEST' | transloco }}</p>
    </div>
  `,
  imports: [TranslocoPipe],
})
export class TranslocoTestComponent {}
