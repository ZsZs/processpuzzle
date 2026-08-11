import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { UnderConstructionComponent } from '@processpuzzle/design';

/** `@processpuzzle/base-state-frontend` is still a scaffold — its public API is a single class — so
 * there is nothing to demonstrate here yet. */
@Component({
  selector: 'base-states-samples',
  standalone: true,
  imports: [CommonModule, TranslocoDirective, UnderConstructionComponent],
  template: `
    <ng-container *transloco="let t; prefix: 'base-states'">
      <div style="margin-bottom: 20px">{{ t('samples_desc_1') }}</div>
      <div>
        <strong>{{ t('samples_desc_2') }}</strong>
      </div>
      <pp-under-construction />
    </ng-container>
  `,
})
export class SamplesComponent {}
