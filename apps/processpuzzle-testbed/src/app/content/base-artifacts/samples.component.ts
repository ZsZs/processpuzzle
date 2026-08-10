import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { UnderConstructionComponent } from '@processpuzzle/design';

/**
 * No live sample yet: `@processpuzzle/base-artifact` ships a container component but no facade, and
 * none for its two embedded port entities either — an Artifact list needs all three registered in
 * `BASE_ENTITY_FACADE_REGISTRY` before it can render. See the base-app samples tab for the shape
 * this one will take once those exist.
 */
@Component({
  selector: 'base-artifacts-samples',
  standalone: true,
  imports: [CommonModule, TranslocoDirective, UnderConstructionComponent],
  template: `
    <ng-container *transloco="let t; prefix: 'base-artifacts'">
      <div style="margin-bottom: 20px">{{ t('samples_desc_1') }}</div>
      <div>
        <strong>{{ t('samples_desc_2') }}</strong>
      </div>
      <pp-under-construction />
    </ng-container>
  `,
})
export class SamplesComponent {}
