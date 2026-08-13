import { Component, inject } from '@angular/core';
import { NavigateBackService } from './navigate-back.service';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';

@Component({
  selector: 'pp-navigate-back',
  imports: [MatIcon, MatIconButton],
  template: `
    <button mat-icon-button aria-label="Go back" (click)="onNavigateBack()">
      <mat-icon class="material-symbols-outlined fat-back-arrow">arrow_back</mat-icon>
    </button>
  `,
  styles: [
    `
      .fat-back-arrow {
        font-size: 24px; /* Größerer und fetterer Pfeil */
        font-variation-settings: 'wght' 1200; /* Fettigkeitsgrad (700 = fett) */
        color: #000; /* Schwarz als Farbe */
      }
    `,
  ],
})
export class NavigateBackComponent {
  readonly service = inject(NavigateBackService);

  // region event handling methods
  onNavigateBack(): void {
    this.service.goBack(); // Call the service to handle navigation
  }

  // endregion
}
