import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatProgressBar } from '@angular/material/progress-bar';
import { provideTranslocoScope, TranslocoPipe } from '@jsverse/transloco';
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
  providers: [ReleaseStore, provideTranslocoScope({ scope: 'design' })],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="release">
      <h2>{{ 'design.release_title' | transloco }}</h2>
      <p class="release__hint">{{ 'design.release_intro' | transloco }}</p>

      @if (store.isBusy()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (store.error(); as error) {
        <p class="release__error" data-testid="release-error">{{ error }}</p>
      }
      @if (store.released(); as released) {
        <p class="release__done" data-testid="release-done">{{ 'design.release_published' | transloco: { number: released.releaseNumber } }}</p>
      }

      <section>
        <h3>{{ 'design.release_pending' | transloco }}</h3>
        @if (store.changesByKind().length === 0) {
          <p data-testid="release-nothing-pending">{{ 'design.release_nothing-pending' | transloco }}</p>
        } @else {
          <table class="release__changes" data-testid="release-changes">
            <thead>
              <tr>
                <th>{{ 'design.release_column_kind' | transloco }}</th>
                <th>{{ 'design.release_column_key' | transloco }}</th>
                <th>{{ 'design.release_column_change' | transloco }}</th>
              </tr>
            </thead>
            <tbody>
              @for (group of store.changesByKind(); track group.kind) {
                @for (change of group.changes; track change.key; let first = $first) {
                  <tr [attr.data-testid]="'release-change-' + change.kind + '-' + change.key">
                    <td>{{ first ? ('design.release_kind_' + change.kind | transloco) : '' }}</td>
                    <td><code>{{ change.key }}</code></td>
                    <td [class]="'change change--' + change.change">{{ 'design.release_change_' + change.change | transloco }}</td>
                  </tr>
                }
              }
            </tbody>
          </table>
        }
      </section>

      @if (store.errors().length || store.warnings().length) {
        <section>
          <h3>{{ 'design.release_problems' | transloco }}</h3>
          <ul class="release__problems" data-testid="release-problems">
            @for (problem of store.errors(); track $index) {
              <li class="problem problem--error">
                <strong>{{ 'design.release_severity_error' | transloco }}</strong> {{ problem.message }}
              </li>
            }
            @for (problem of store.warnings(); track $index) {
              <li class="problem problem--warning">
                <strong>{{ 'design.release_severity_warning' | transloco }}</strong> {{ problem.message }}
              </li>
            }
          </ul>
          @if (store.warnings().length && !store.errors().length) {
            <mat-checkbox data-testid="release-acknowledge" [checked]="store.warningsAcknowledged()" (change)="store.acknowledgeWarnings($event.checked)">
              {{ 'design.release_acknowledge' | transloco }}
            </mat-checkbox>
          }
        </section>
      }

      <div class="release__actions">
        <button mat-raised-button color="accent" type="button" data-testid="release-publish" [disabled]="!store.canPublish()" (click)="store.publish()">
          {{ 'design.release_publish' | transloco }}
        </button>
      </div>

      <section>
        <h3>{{ 'design.release_history' | transloco }}</h3>
        @if (store.history().length === 0) {
          <p data-testid="release-no-history">{{ 'design.release_no-history' | transloco }}</p>
        } @else {
          <ul class="release__history" data-testid="release-history">
            @for (release of store.history(); track release.releaseNumber) {
              <li>
                <strong>#{{ release.releaseNumber }}</strong>
                {{ release.releasedAt | date: 'medium' }}
                @if (release.releasedBy) {
                  · {{ 'design.release_by' | transloco: { name: release.releasedBy } }}
                }
                · {{ 'design.release_items' | transloco: { count: release.items.length } }}
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
