import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatProgressBar } from '@angular/material/progress-bar';
import { provideTranslocoScope, TranslocoPipe } from '@jsverse/transloco';
import { BASE_RELEASE_TRANSLOCO_SCOPE } from '../base-release.i18n';
import { ReleaseStore } from './release.store';

/**
 * Publishes the organization: every definition the designer changed since the last release goes live at once,
 * or nothing does. Shows what would be released, by kind; the problems that refuse it (errors) or need
 * confirming (warnings); and the releases made so far.
 *
 * The live application reads only released definitions, so this is the one place a change reaches end users.
 */
@Component({
  selector: 'pp-release-panel',
  standalone: true,
  imports: [TranslocoPipe, DatePipe, MatButton, MatCheckbox, MatProgressBar],
  providers: [ReleaseStore, provideTranslocoScope({ scope: BASE_RELEASE_TRANSLOCO_SCOPE, alias: BASE_RELEASE_TRANSLOCO_SCOPE })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="release">
      <h2>{{ 'base_release.release.title' | transloco }}</h2>
      <p class="release__hint">{{ 'base_release.release.intro' | transloco }}</p>

      @if (store.isBusy()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (store.error(); as error) {
        <p class="release__error" data-testid="release-error">{{ error }}</p>
      }
      @if (store.released(); as released) {
        <p class="release__done" data-testid="release-done">{{ 'base_release.release.published' | transloco: { number: released.releaseNumber } }}</p>
      }

      <section>
        <h3>{{ 'base_release.release.pending' | transloco }}</h3>
        @if (store.changesByKind().length === 0) {
          <p data-testid="release-nothing-pending">{{ 'base_release.release.nothing-pending' | transloco }}</p>
        } @else {
          <table class="release__changes" data-testid="release-changes">
            <thead>
              <tr>
                <th>{{ 'base_release.release.column_kind' | transloco }}</th>
                <th>{{ 'base_release.release.column_key' | transloco }}</th>
                <th>{{ 'base_release.release.column_change' | transloco }}</th>
              </tr>
            </thead>
            <tbody>
              @for (group of store.changesByKind(); track group.kind) {
                @for (change of group.changes; track change.key; let first = $first) {
                  <tr [attr.data-testid]="'release-change-' + change.kind + '-' + change.key">
                    <td>{{ first ? ('base_release.release.kind_' + change.kind | transloco) : '' }}</td>
                    <td><code>{{ change.key }}</code></td>
                    <td [class]="'change change--' + change.change">{{ 'base_release.release.change_' + change.change | transloco }}</td>
                  </tr>
                }
              }
            </tbody>
          </table>
        }
      </section>

      @if (store.errors().length || store.warnings().length) {
        <section>
          <h3>{{ 'base_release.release.problems' | transloco }}</h3>
          <ul class="release__problems" data-testid="release-problems">
            @for (problem of store.errors(); track $index) {
              <li class="problem problem--error">
                <strong>{{ 'base_release.release.severity_error' | transloco }}</strong> {{ problem.message }}
              </li>
            }
            @for (problem of store.warnings(); track $index) {
              <li class="problem problem--warning">
                <strong>{{ 'base_release.release.severity_warning' | transloco }}</strong> {{ problem.message }}
              </li>
            }
          </ul>
          @if (store.warnings().length && !store.errors().length) {
            <mat-checkbox data-testid="release-acknowledge" [checked]="store.warningsAcknowledged()" (change)="store.acknowledgeWarnings($event.checked)">
              {{ 'base_release.release.acknowledge' | transloco }}
            </mat-checkbox>
          }
        </section>
      }

      <div class="release__actions">
        <button mat-raised-button color="accent" type="button" data-testid="release-publish" [disabled]="!store.canPublish()" (click)="store.publish()">
          {{ 'base_release.release.publish' | transloco }}
        </button>
      </div>

      <section>
        <h3>{{ 'base_release.release.history' | transloco }}</h3>
        @if (store.history().length === 0) {
          <p data-testid="release-no-history">{{ 'base_release.release.no-history' | transloco }}</p>
        } @else {
          <ul class="release__history" data-testid="release-history">
            @for (release of store.history(); track release.releaseNumber) {
              <li>
                <strong>#{{ release.releaseNumber }}</strong>
                {{ release.releasedAt | date: 'medium' }}
                @if (release.releasedBy) {
                  · {{ 'base_release.release.by' | transloco: { name: release.releasedBy } }}
                }
                · {{ 'base_release.release.items' | transloco: { count: release.items.length } }}
              </li>
            }
          </ul>
        }
      </section>
    </div>
  `,
  styles: `
    .release {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 16px;
      max-width: 960px;
    }
    .release__hint {
      color: #666666;
    }
    .release__error,
    .problem--error {
      color: #c62828;
    }
    .problem--warning {
      color: #ef6c00;
    }
    .release__done {
      color: #2e7d32;
    }
    .release__changes {
      border-collapse: collapse;
      font-size: 13px;
    }
    .release__changes th,
    .release__changes td {
      text-align: left;
      padding: 4px 16px 4px 0;
      border-bottom: 1px solid #eeeeee;
    }
    .change--added {
      color: #2e7d32;
    }
    .change--changed {
      color: #ef6c00;
    }
    .change--removed {
      color: #c62828;
    }
  `,
})
export class ReleasePanelComponent implements OnInit {
  protected readonly store = inject(ReleaseStore);

  ngOnInit(): void {
    void this.store.load();
  }
}
