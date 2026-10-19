import { Component, DestroyRef, inject } from '@angular/core';
import { CdkPortalOutlet } from '@angular/cdk/portal';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { SidenavContentService } from './sidenav-content.service';

/**
 * The sidenav's half of {@link SidenavContentService}. A shell wraps its menu in this element: with nothing
 * attached the menu is all it renders; once a page attaches content, a slim header with the content's title
 * and a toggle appears above, and the toggle switches the body between the attached content and the menu.
 */
@Component({
  selector: 'pp-sidenav-content-outlet',
  template: `
    <ng-container *transloco="let t; prefix: 'widgets.sidenav_content'">
      @if (sidenavContent.portal()) {
        <div class="pp-sidenav-content__header">
          <span class="pp-sidenav-content__title" data-testid="sidenav-content-title">{{ sidenavContent.active() ? sidenavContent.title() : '' }}</span>
          <button
            mat-icon-button
            type="button"
            data-testid="sidenav-content-toggle"
            [matTooltip]="sidenavContent.active() ? t('show_menu') : t('show_content')"
            [attr.aria-label]="sidenavContent.active() ? t('show_menu') : t('show_content')"
            (click)="sidenavContent.toggle()"
          >
            <mat-icon>{{ sidenavContent.active() ? 'menu' : 'account_tree' }}</mat-icon>
          </button>
        </div>
      }
    </ng-container>
    @if (sidenavContent.active()) {
      <div class="pp-sidenav-content__body" data-testid="sidenav-content-body">
        <ng-template [cdkPortalOutlet]="sidenavContent.portal()" />
      </div>
    } @else {
      <ng-content />
    }
  `,
  styles: `
    :host {
      display: block;
    }
    .pp-sidenav-content__header {
      align-items: center;
      color: var(--pp-on-sidenav, inherit);
      display: flex;
      gap: 4px;
      justify-content: space-between;
      padding-inline-start: 12px;
    }
    .pp-sidenav-content__title {
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `,
  imports: [CdkPortalOutlet, MatIconButton, MatIcon, MatTooltip, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'widgets', alias: 'widgets' })],
})
export class SidenavContentOutletComponent {
  protected readonly sidenavContent = inject(SidenavContentService);

  constructor() {
    inject(DestroyRef).onDestroy(this.sidenavContent.registerOutlet());
  }
}
