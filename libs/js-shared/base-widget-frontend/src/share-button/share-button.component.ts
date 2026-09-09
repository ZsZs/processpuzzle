import { Component } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { ShareButtons } from 'ngx-sharebuttons/buttons';

@Component({
  selector: 'pp-share-button',
  template: `
    <div>
      <button mat-icon-button (click)="onShare()" cdkOverlayOrigin #trigger="cdkOverlayOrigin" aria-label="Share Button">
        <mat-icon>share</mat-icon>
      </button>
    </div>
    <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="trigger" [cdkConnectedOverlayOpen]="isOpen" [cdkConnectedOverlayHasBackdrop]="true" (backdropClick)="onClose()">
      <div class="share-buttons-container">
        <share-buttons />
      </div>
    </ng-template>
  `,
  styleUrls: ['./share-button.component.css'],
  imports: [CdkOverlayOrigin, CdkConnectedOverlay, MatIcon, MatIconButton, ShareButtons],
})
export class ShareButtonComponent {
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
