import { Component } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { LanguageSelectorListComponent } from './language-selector-list.component';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';

@Component({
  selector: 'pp-language-selector',
  template: `
    <ng-container *transloco="let t; prefix: 'language-selector'">
      <div>
        <button mat-icon-button (click)="onSelectLanguage()" cdkOverlayOrigin #trigger="cdkOverlayOrigin" aria-label="Select Language Button">
          <mat-icon>language</mat-icon>
        </button>
      </div>
      <ng-template cdkConnectedOverlay [cdkConnectedOverlayOrigin]="trigger" [cdkConnectedOverlayOpen]="isOpen" [cdkConnectedOverlayHasBackdrop]="true" (backdropClick)="onClose()">
        <div class="language-selector-container">
          <pp-language-selector-list />
        </div>
      </ng-template>
    </ng-container>
  `,
  styleUrls: ['./language-selector.component.css'],
  imports: [CdkOverlayOrigin, CdkConnectedOverlay, MatIcon, MatIconButton, LanguageSelectorListComponent, TranslocoDirective],
  providers: [provideTranslocoScope('widgets')],
})
export class LanguageSelectorComponent {
  isOpen = false;

  // region Event handler methods
  onClose(): void {
    this.isOpen = false;
  }

  onSelectLanguage(): void {
    this.toggleIsOpen();
  }

  // endregion

  // region protected, private helper methods
  private toggleIsOpen(): void {
    this.isOpen = !this.isOpen;
  }

  // endregion
}
