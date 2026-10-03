import { Component, computed, viewChild } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { SampleHostComponent, SampleTab } from '../common/sample-host.component';
import { BOAT_NAME, BOAT_PATH } from './boat-sample.routes';

/** A photo of the seeded boat CAN 603, a 49er, from Wikimedia Commons. */
export const SAMPLE_PHOTO_URL = 'https://commons.wikimedia.org/wiki/File:49er_sail_boats.jpg';

/**
 * Samples of the Base AI section, as a user meets the feature: first the `Recognition Profile` that says how
 * boats are recognized, then a `Boat` itself, whose Enrollment tab is where its photos go.
 *
 * Both are the framework's generated screens — the profile's from `BASE_AI_ROUTES`, the boat's resolved at
 * run-time from its base-entity definition — so the walkthrough above the toggle is the only thing written
 * for this page.
 */
@Component({
  selector: 'base-ai-samples',
  standalone: true,
  imports: [SampleHostComponent, TranslocoDirective],
  template: `
    <pp-sample-host prefix="base-ai" groupName="aiSample" ariaLabel="Image Recognition Sample" [tabs]="tabs">
      <div sample-header class="walkthrough" *transloco="let t; prefix: 'base-ai'">
        <strong>{{ t('samples_steps_heading') }}</strong>
        <ol>
          <li>{{ t('samples_step_1') }}</li>
          <li>{{ t('samples_step_2') }}</li>
          <li>
            {{ t('samples_step_3') }}
            <a [href]="samplePhotoUrl" target="_blank" rel="noopener">{{ t('samples_photo_link') }}</a>
          </li>
          <li>{{ t('samples_step_4') }}</li>
          <li>{{ t('samples_step_5') }}</li>
        </ol>
      </div>
    </pp-sample-host>
  `,
  styles: `
    .walkthrough {
      margin: 0 0 16px;
      line-height: 1.6;
      max-width: 900px;
    }
    .walkthrough ol {
      margin: 4px 0 0;
      padding-left: 20px;
    }
  `,
})
export class SamplesComponent {
  private readonly host = viewChild(SampleHostComponent);
  protected readonly samplePhotoUrl = SAMPLE_PHOTO_URL;
  readonly tabs: SampleTab[] = [
    { route: 'recognition-profile', label: 'Recognition Profile' },
    { route: BOAT_PATH, label: BOAT_NAME },
  ];
  readonly selectedButton = computed(() => this.host()?.selectedButton() ?? '');
}
