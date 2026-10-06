import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { STARTER_IMPORT_I18N_SCOPE } from '../base-starter.i18n';
import { StarterImportStore } from '../domain/starter-import.store';
import { ImportReportComponent } from './import-report.component';
import { InstalledStartersComponent } from './installed-starters.component';

/**
 * Installs a Business Starter from the catalog into the current organization: pick a starter and version,
 * preview what it would do (a dry run), then install it. Below, the starter the organization has.
 *
 * Installing replaces the organization's definitions, and the backend refuses it once the organization holds
 * entity objects or workflow instances — the preview says so before anything is written.
 */
@Component({
  selector: 'pp-starter-import',
  standalone: true,
  imports: [TranslocoPipe, MatButton, MatProgressBar, ImportReportComponent, InstalledStartersComponent],
  providers: [StarterImportStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="starter-import">
      <h2 class="starter-import__title">{{ scope + '.title' | transloco }}</h2>
      <p class="starter-import__hint">{{ scope + '.hint' | transloco }}</p>

      @if (store.catalog().length === 0) {
        <p class="starter-import__empty" data-testid="starter-catalog-empty">{{ scope + '.catalogEmpty' | transloco }}</p>
      } @else {
        <table class="starter-import__catalog" data-testid="starter-catalog">
          <thead>
            <tr>
              <th></th>
              <th>{{ scope + '.column.starter' | transloco }}</th>
              <th>{{ scope + '.column.description' | transloco }}</th>
              <th>{{ scope + '.column.author' | transloco }}</th>
              <th>{{ scope + '.column.license' | transloco }}</th>
            </tr>
          </thead>
          <tbody>
            @for (starter of store.catalog(); track starter.id) {
              <tr [attr.data-testid]="'catalog-starter-' + starter.id" [class.starter-import__row--selected]="store.selection()?.starterId === starter.id">
                <td>
                  <input
                    type="radio"
                    name="starter"
                    [attr.data-testid]="'catalog-select-' + starter.id"
                    [checked]="store.selection()?.starterId === starter.id"
                    (change)="store.select(starter.id)"
                  />
                </td>
                <td>{{ starter.name }}</td>
                <td>{{ starter.description }}</td>
                <td>{{ starter.author ?? '—' }}</td>
                <td>{{ starter.license ?? '—' }}</td>
              </tr>
            }
          </tbody>
        </table>
      }

      <div class="starter-import__actions">
        @if (store.selectedStarter(); as starter) {
          <label class="starter-import__version">
            {{ scope + '.version' | transloco }}
            <select data-testid="starter-version" (change)="pickVersion(starter.id, $event)">
              @for (version of starter.versions; track version.version) {
                <option [value]="version.version" [selected]="version.version === store.selection()?.version">
                  {{ version.version }}{{ version.status === 'deprecated' ? ' (' + (scope + '.deprecated' | transloco) + ')' : '' }}
                </option>
              }
            </select>
          </label>
        }
        <button mat-stroked-button type="button" data-testid="starter-preview" [disabled]="!store.canPreview()" (click)="store.preview()">
          {{ scope + '.preview' | transloco }}
        </button>
        <button mat-flat-button type="button" data-testid="starter-apply" [disabled]="!store.canApply()" (click)="store.apply()">
          {{ scope + '.apply' | transloco }}
        </button>
      </div>

      @if (store.isBusy()) {
        <mat-progress-bar mode="indeterminate" />
      }
      @if (store.error(); as error) {
        <p class="starter-import__error" data-testid="starter-import-error">{{ error }}</p>
      }
      @if (store.report(); as report) {
        <pp-import-report [report]="report" />
      }

      <pp-installed-starters [starters]="store.installed()" />
    </div>
  `,
  styles: `
    .starter-import {
      display: flex;
      flex-direction: column;
      gap: 12px;
      background-color: #ffffff;
      border-radius: 6px;
      padding: 16px 20px 24px;
    }
    .starter-import__title {
      margin: 0;
      font-size: 18px;
    }
    .starter-import__hint,
    .starter-import__empty {
      margin: 0;
      color: #666666;
    }
    .starter-import__catalog {
      border-collapse: collapse;
      font-size: 13px;
    }
    .starter-import__catalog th,
    .starter-import__catalog td {
      text-align: left;
      padding: 4px 16px 4px 0;
      border-bottom: 1px solid #eeeeee;
    }
    .starter-import__row--selected {
      background-color: #f3f8fd;
    }
    .starter-import__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
    }
    .starter-import__version {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .starter-import__error {
      color: #c62828;
    }
  `,
})
export class StarterImportComponent implements OnInit {
  protected readonly store = inject(StarterImportStore);
  protected readonly scope = STARTER_IMPORT_I18N_SCOPE;

  ngOnInit(): void {
    void this.store.loadCatalog();
    void this.store.loadInstalled();
  }

  protected pickVersion(starterId: string, event: Event): void {
    this.store.select(starterId, (event.target as HTMLSelectElement).value);
  }
}
