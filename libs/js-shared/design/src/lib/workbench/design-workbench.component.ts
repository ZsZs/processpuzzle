import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatMiniFabButton } from '@angular/material/button';
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { AssistantPanelComponent } from '@processpuzzle/base-ai';
import { DESIGN_ROUTE_PREFIX } from '@processpuzzle/util';
import { AnnouncementComponent, AnnouncementTone } from '@processpuzzle/widgets';
import { filter, map } from 'rxjs';
import { DESIGN_ASSISTANT_ENABLED } from './design-assistant.provider';

/**
 * A notice a designer section asks to show above itself, set as `data: { announcement }` on its route. `messageKey` and
 * `linkKey` are keys of the `design` translations, without the scope; `link` is a path under the design, such as
 * `/design/tutorials/entity-designer`. The `id` is what a dismissal is remembered under.
 */
export interface DesignAnnouncement {
  id: string;
  messageKey: string;
  linkKey?: string;
  link?: string;
  tone?: AnnouncementTone;
}

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
  imports: [AnnouncementComponent, AssistantPanelComponent, MatMiniFabButton, MatSidenav, MatSidenavContainer, MatSidenavContent, RouterOutlet, TranslocoDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <mat-sidenav-container class="pp-design-workbench" [hasBackdrop]="false">
      <mat-sidenav-content class="pp-design-workbench__content">
        @if (announcement(); as note) {
          <ng-container *transloco="let t; scope: 'design'; prefix: 'design'">
            <pp-announcement
              [announcementId]="note.id"
              [message]="t(note.messageKey)"
              [linkLabel]="note.linkKey ? t(note.linkKey) : ''"
              [link]="note.link ? prefix + note.link : ''"
              [tone]="note.tone ?? 'info'"
            />
          </ng-container>
        }
        <router-outlet />
        @if (assistantEnabled) {
          <ng-container *transloco="let t; scope: 'design'; prefix: 'design'">
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
  protected readonly prefix = inject(DESIGN_ROUTE_PREFIX);

  private readonly route = inject(ActivatedRoute);
  /** The announcement of the section open now: the nearest one down the route chain, so a child may override its parent. */
  protected readonly announcement = toSignal(
    inject(Router).events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.announcementOfOpenSection()),
    ),
    { initialValue: this.announcementOfOpenSection() },
  );

  private announcementOfOpenSection(): DesignAnnouncement | undefined {
    let found: DesignAnnouncement | undefined;
    for (let route: ActivatedRoute | null = this.route; route; route = route.firstChild) {
      found = (route.snapshot.data['announcement'] as DesignAnnouncement | undefined) ?? found;
    }
    return found;
  }

  protected toggleAssistant(): void {
    this.assistantOpen.update((open) => !open);
  }
}
