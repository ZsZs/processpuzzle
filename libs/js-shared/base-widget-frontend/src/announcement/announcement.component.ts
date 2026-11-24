import { Component, effect, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButton } from '@angular/material/button';
import { TranslocoPipe } from '@jsverse/transloco';

/** The tone of an announcement: `info` is the light green of a tip, `warning` the amber of a notice such as maintenance. */
export type AnnouncementTone = 'info' | 'warning';

/** Where an announcement's dismissal is remembered. `Hide` lasts for the browser session; `Don't show again` for good. */
const SESSION_KEY = (id: string) => `pp.announcement.hidden.${id}`;
const PERMANENT_KEY = (id: string) => `pp.announcement.dismissed.${id}`;

function isDismissed(id: string): boolean {
  return sessionStorage.getItem(SESSION_KEY(id)) !== null || localStorage.getItem(PERMANENT_KEY(id)) !== null;
}

/**
 * A notice bar across the top of a page: a tip such as "tutorials are available", or a notice such as a maintenance
 * window. The text and the link arrive already translated, so the bar knows nothing of the feature that announces;
 * only its two buttons, which every announcement shares, are translated here.
 *
 * `Hide` removes the bar until the browser session ends. `Don't show again` removes it for good in this browser.
 * Both are remembered under the `announcementId`, so a new id is a new announcement that everyone sees once.
 */
@Component({
  selector: 'pp-announcement',
  standalone: true,
  imports: [MatButton, RouterLink, TranslocoPipe],
  template: `
    @if (visible()) {
      <div class="pp-announcement" [class.pp-announcement--warning]="tone() === 'warning'" role="status" data-testid="announcement">
        <span class="pp-announcement__message">{{ message() }}</span>
        @if (link() && linkLabel()) {
          <a class="pp-announcement__link" [routerLink]="link()">{{ linkLabel() }}</a>
        }
        <span class="pp-announcement__actions">
          <button mat-button type="button" data-testid="announcement-hide" (click)="hide()">{{ 'widgets.announcement.hide' | transloco }}</button>
          <button mat-button type="button" data-testid="announcement-dont-show-again" (click)="dontShowAgain()">
            {{ 'widgets.announcement.dont_show_again' | transloco }}
          </button>
        </span>
      </div>
    }
  `,
  styles: [
    `
      .pp-announcement {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px;
        padding: 8px 16px;
        background: var(--pp-color-light-green);
        color: var(--pp-button-primary-bg, inherit);
      }
      .pp-announcement--warning {
        background: #ffe082;
        color: #3e2723;
      }
      .pp-announcement__message {
        flex: 1 1 auto;
      }
      .pp-announcement__link {
        font-weight: 500;
        color: inherit;
      }
      .pp-announcement__actions {
        display: flex;
        gap: 4px;
        margin-inline-start: auto;
      }
    `,
  ],
})
export class AnnouncementComponent {
  /** Identifies the announcement for dismissal. Change it to show the announcement again to everyone. */
  readonly announcementId = input.required<string>();
  /** The sentence the bar shows, already translated. */
  readonly message = input.required<string>();
  /** The link's label, already translated. The link is shown only when both are given. */
  readonly linkLabel = input<string>('');
  /** Where the link leads: an application path, such as `/design/tutorials/entity-designer`. */
  readonly link = input<string>('');
  readonly tone = input<AnnouncementTone>('info');

  readonly visible = signal(false);

  constructor() {
    effect(() => this.visible.set(!isDismissed(this.announcementId())));
  }

  hide(): void {
    sessionStorage.setItem(SESSION_KEY(this.announcementId()), 'true');
    this.visible.set(false);
  }

  dontShowAgain(): void {
    localStorage.setItem(PERMANENT_KEY(this.announcementId()), 'true');
    this.visible.set(false);
  }
}
