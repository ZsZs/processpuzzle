import { Component, computed, inject } from '@angular/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { SIDENAV_CONTROL } from './sidenav-control';

/**
 * An icon button that shows or hides the sidenav of the application it sits in — for its header or footer.
 * It talks to the nearest {@link SIDENAV_CONTROL}; with none, or with no sidenav to control, it renders nothing.
 */
@Component({
  selector: 'pp-sidenav-toggle',
  template: `
    @if (visible()) {
      <ng-container *transloco="let t; prefix: 'widgets.sidenav_toggle'">
        <button
          mat-icon-button
          type="button"
          test-id="sidenav-toggle"
          [attr.aria-label]="control?.opened() ? t('hide') : t('show')"
          [attr.aria-expanded]="control?.opened()"
          (click)="control?.toggle()"
        >
          <mat-icon>{{ control?.opened() ? 'menu_open' : 'menu' }}</mat-icon>
        </button>
      </ng-container>
    }
  `,
  imports: [MatIconButton, MatIcon, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'widgets', alias: 'widgets' })],
})
export class SidenavToggleComponent {
  protected readonly control = inject(SIDENAV_CONTROL, { optional: true });
  protected readonly visible = computed(() => this.control?.available() ?? false);
}
