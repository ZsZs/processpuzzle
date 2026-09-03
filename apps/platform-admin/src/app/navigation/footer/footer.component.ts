import { Component } from '@angular/core';
import { MatToolbar } from '@angular/material/toolbar';
import { VersionButtonComponent } from '@processpuzzle/base-widget';

/**
 * Nothing but the version button, which reads `APPLICATION_VERSION` out of `RUNTIME_CONFIGURATION`
 * — so a deployed bundle can be identified without a build log.
 */
@Component({
  selector: 'app-footer',
  imports: [MatToolbar, VersionButtonComponent],
  template: `
    <mat-toolbar class="app-footer">
      <span class="spacer"></span>
      <pp-version-button />
    </mat-toolbar>
  `,
  styleUrl: './footer.component.scss',
})
export class FooterComponent {}
