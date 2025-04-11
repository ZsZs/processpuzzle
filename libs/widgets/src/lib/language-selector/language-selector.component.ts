import { Component } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { LanguageSelectorListComponent } from './language-selector-list.component';

@Component({
  selector: 'pp-language-selector',
  template: `
    <div>
      <button mat-icon-button (click)="onShare()" cdkOverlayOrigin #trigger="cdkOverlayOrigin" aria-label="Share Button">
        <mat-icon>language</mat-icon>
      </button>
    </div>
    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="trigger" [cdkConnectedOverlayOpen]="isOpen" [cdkConnectedOverlayHasBackdrop]="true" (backdropClick)="onClose()">
      <div class="language-selector-container">
        <pp-language-selector-list />
      </div>
    </ng-template>
  `,
  styleUrls: ['./language-selector.component.css'],
  imports: [CdkOverlayOrigin, CdkConnectedOverlay, MatIcon, MatIconButton, LanguageSelectorListComponent],
  providers: [],
})
export class LanguageSelectorComponent {
  isOpen = false;

  // region Event handler methods
  onClose(): void {
    this.isOpen = false;
  }

  onShare(): void {
    this.toggleIsOpen();
  }

  // endregion

  // region protected, private helper methods
  private toggleIsOpen(): void {
    this.isOpen = !this.isOpen;
  }

  // endregion
}
