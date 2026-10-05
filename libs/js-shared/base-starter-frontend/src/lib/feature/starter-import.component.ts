import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslocoPipe } from '@jsverse/transloco';
import { STARTER_IMPORT_I18N_SCOPE } from '../base-starter.i18n';
import { StarterImportStore } from '../domain/starter-import.store';
import { ImportReportComponent } from './import-report.component';
import { InstalledStartersComponent } from './installed-starters.component';

/**
 * Imports a Business Starter bundle into the current organization: pick a zip, preview what it would do
 * (a dry run), then import it. Below, the starters the organization already has.
 *
 * Installing *from the catalog* is not here — the catalog lives in processpuzzle-biz, which fetches the
 * bundle and calls the same endpoint. This screen is the file-upload path of the Design view.
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

      <div class="starter-import__actions">
        <label class="starter-import__file">
          <input type="file" accept=".zip,application/zip" data-testid="starter-bundle-input" (change)="pick($event)" />
        </label>
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
    .starter-import__hint {
      margin: 0;
      color: #666666;
    }
    .starter-import__actions {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 12px;
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
    void this.store.loadInstalled();
  }

  protected pick(event: Event): void {
    this.store.selectBundle((event.target as HTMLInputElement).files?.[0] ?? undefined);
  }
}
