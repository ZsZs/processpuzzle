import { Component } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { LanguageSelectorListComponent } from './language-selector-list.component';
import { provideTranslocoScope } from '@jsverse/transloco';
import { MatMenu, MatMenuTrigger } from '@angular/material/menu';

@Component({
  selector: 'pp-language-selector',
  template: `
    <div class="language-selector-container">
      <button mat-icon-button [matMenuTriggerFor]="langMenu" aria-label="Select Language Button">
        <mat-icon>language</mat-icon>
      </button>
      <mat-menu #langMenu="matMenu">
        <ng-container (click)="$event.stopPropagation()">
          <pp-language-selector-list (languageSelected)="langMenu.closed.emit()" />
        </ng-container>
      </mat-menu>
    </div>
  `,
  styleUrls: ['./language-selector.component.css'],
  imports: [MatIcon, MatIconButton, LanguageSelectorListComponent, MatMenuTrigger, MatMenu],
  providers: [provideTranslocoScope({ scope: 'widgets' })],
})
export class LanguageSelectorComponent {
  // region Event handler methods
  // endregion
  // region protected, private helper methods
  // endregion
}
