import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatMiniFabButton } from '@angular/material/button';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { RouterOutlet } from '@angular/router';
import { provideTranslocoScope, TranslocoDirective } from '@jsverse/transloco';
import { AssistantPanelComponent } from '@processpuzzle/base-ai';
import { DESIGN_ASSISTANT_ENABLED } from './design-assistant.provider';

/**
 * The frame every design section renders in: the section itself, and — when the application provides it — the
 * design assistant in a drawer at the end, so that a designer can describe what to define while looking at the
 * designer that will show it. The drawer is the one place for the assistant beside *every* section, including the
 * ones other libraries mount here, which is why it is a route wrapper rather than a part of each designer page.
 *
 * Without {@link DESIGN_ASSISTANT_ENABLED} there is no drawer and no toggle: the sections render as they did before
 * the workbench existed. The drawer keeps its own conversation while the designer moves between sections, because the
 * panel lives in the wrapper, not in the sections' outlet.
 */
@Component({
  selector: 'pp-design-workbench',
  imports: [AssistantPanelComponent, MatMiniFabButton, MatSidenav, MatSidenavContainer, MatSidenavContent, RouterOutlet, TranslocoDirective],
  providers: [provideTranslocoScope({ scope: 'design' })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-sidenav-container class="pp-design-workbench" [hasBackdrop]="false">
      <mat-sidenav-content class="pp-design-workbench__content">
        <router-outlet />
        @if (assistantEnabled) {
          <ng-container *transloco="let t; prefix: 'design'">
            <button
              matMiniFab
              type="button"
              class="pp-design-workbench__toggle"
              [class.pp-design-workbench__toggle--open]="assistantOpen()"
              [attr.aria-label]="t('assistant_toggle')"
              [attr.aria-pressed]="assistantOpen()"
              (click)="toggleAssistant()"
            >
              <span class="material-symbols-outlined" aria-hidden="true">smart_toy</span>
            </button>
          </ng-container>
        }
      </mat-sidenav-content>
      @if (assistantEnabled) {
        <mat-sidenav class="pp-design-workbench__assistant" position="end" mode="side" [opened]="assistantOpen()" [disableClose]="true">
          <pp-assistant-panel />
        </mat-sidenav>
      }
    </mat-sidenav-container>
  `,
  styleUrl: 'design-workbench.component.scss',
})
export class DesignWorkbenchComponent {
  protected readonly assistantEnabled = inject(DESIGN_ASSISTANT_ENABLED);
  protected readonly assistantOpen = signal(false);

  protected toggleAssistant(): void {
    this.assistantOpen.update((open) => !open);
  }
}
