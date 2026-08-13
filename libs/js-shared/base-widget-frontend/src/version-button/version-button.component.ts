import { Component, inject } from '@angular/core';
import { BaseConfiguration, RUNTIME_CONFIGURATION } from '@processpuzzle/util';

@Component({
  selector: 'pp-version-button',
  template: ` <span class="version-button">v{{ version }}</span> `,
  styleUrls: ['./version-button.component.css'],
})
export class VersionButtonComponent {
  private readonly runtimeConfig = inject<{ BASE_CONFIGURATION: BaseConfiguration }>(RUNTIME_CONFIGURATION);
  readonly version = this.runtimeConfig.BASE_CONFIGURATION.APPLICATION_VERSION;
}
