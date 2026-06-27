import { Component, computed, signal } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatIconButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'pp-design-button',
  template: `
    <div class="design-button">
      <button mat-icon-button [routerLink]="routerLink()" [attr.aria-label]="ariaLabel()" (click)="toggle()">
        <mat-icon>{{ icon() }}</mat-icon>
      </button>
    </div>
  `,
  styleUrls: ['./design-button.component.css'],
  imports: [MatIcon, MatIconButton, RouterLink],
})
export class DesignButtonComponent {
  protected readonly designMode = signal(false);
  protected readonly icon = computed(() => (this.designMode() ? 'home' : 'design_services'));
  protected readonly routerLink = computed(() => (this.designMode() ? ['/home'] : ['/design']));
  protected readonly ariaLabel = computed(() => (this.designMode() ? 'Home Button' : 'Design Button'));

  toggle(): void {
    this.designMode.update((value) => !value);
  }
}
