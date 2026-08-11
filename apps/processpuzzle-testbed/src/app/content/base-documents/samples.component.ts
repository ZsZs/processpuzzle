import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslocoDirective } from '@jsverse/transloco';
// eslint-disable-next-line @nx/enforce-module-boundaries
import { UnderConstructionComponent } from '@processpuzzle/design';

/**
 * No sample of its own yet. The blocker is gone — `@processpuzzle/base-document` now ships
 * `DocumentFacade` and the two port facades, which `app.config.ts` registers in
 * `BASE_ENTITY_FACADE_REGISTRY` — so the live screen is reachable at `/design/document`, mounted by
 * `BASE_DOCUMENT_ROUTES`. What is left for this tab is a testbed-local sample document to drive it,
 * shaped like the base-app samples tab.
 */
@Component({
  selector: 'base-documents-samples',
  standalone: true,
  imports: [CommonModule, TranslocoDirective, UnderConstructionComponent],
  template: `
    <ng-container *transloco="let t; prefix: 'base-documents'">
      <div style="margin-bottom: 20px">{{ t('samples_desc_1') }}</div>
      <div>
        <strong>{{ t('samples_desc_2') }}</strong>
      </div>
      <pp-under-construction />
    </ng-container>
  `,
})
export class SamplesComponent {}
