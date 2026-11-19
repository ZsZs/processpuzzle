import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { STARTER_IMPORT_I18N_SCOPE, STARTER_UNINSTALL_I18N_SCOPE } from '../base-starter.i18n';
import { ImportAction, ImportReport } from '../domain/starter';

/**
 * One `ImportReport`: its outcome, the definitions it touched, or why it was refused, and what the install could not do
 * for want of a place. Presentational. The report of removing a starter has the same shape and takes its status and summary
 * words from the uninstall scope: "would install" would be wrong for it.
 */
@Component({
  selector: 'pp-import-report',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = report();
    <section class="report" [class]="'report report--' + r.status" data-testid="import-report">
      <header class="report__header">
        <strong data-testid="import-report-status">{{ wordScope() + '.status.' + r.status | transloco }}</strong>
        @if (r.starterId) {
          <span class="report__starter">{{ r.starterId }} {{ r.version }}</span>
        }
        @if (r.status !== 'rejected') {
          <span data-testid="import-report-summary">{{ wordScope() + '.summary' | transloco: { created: count('create'), updated: count('update'), deleted: count('delete'), removed: count('delete') } }}</span>
        }
      </header>

      @if (r.errors?.length) {
        <ul class="report__errors" data-testid="import-report-errors">
          @for (error of r.errors; track $index) {
            <li>
              @if (error.file) {
                <code>{{ error.file }}</code>:
              }
              {{ error.message }}
            </li>
          }
        </ul>
      }

      @if (r.warnings?.length) {
        <ul class="report__warnings" data-testid="import-report-warnings">
          @for (warning of r.warnings; track $index) {
            <li>{{ warning }}</li>
          }
        </ul>
      }

      @if (r.items?.length) {
        <table class="report__items" data-testid="import-report-items">
          <thead>
            <tr>
              <th>{{ scope + '.column.kind' | transloco }}</th>
              <th>{{ scope + '.column.key' | transloco }}</th>
              <th>{{ scope + '.column.action' | transloco }}</th>
            </tr>
          </thead>
          <tbody>
            @for (item of r.items; track item.kind + ':' + item.key) {
              <tr>
                <td>{{ scope + '.kind.' + item.kind | transloco }}</td>
                <td><code>{{ item.key }}</code></td>
                <td [class]="'action action--' + item.action">{{ scope + '.action.' + item.action | transloco }}</td>
              </tr>
            }
          </tbody>
        </table>
      }
    </section>
  `,
  styles: `
    .report {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 12px 16px;
      border-radius: 6px;
      border-left: 4px solid var(--pp-color-dark-blue, rgb(24, 111, 206));
      background-color: #ffffff;
    }
    .report--rejected {
      border-left-color: #c62828;
    }
    .report--applied {
      border-left-color: #2e7d32;
    }
    .report__header {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: baseline;
    }
    .report__starter {
      color: #666666;
    }
    .report__errors {
      margin: 0;
      padding-left: 20px;
      color: #c62828;
    }
    .report__warnings {
      margin: 0;
      padding-left: 20px;
      color: #ef6c00;
    }
    .report__items {
      border-collapse: collapse;
      font-size: 13px;
    }
    .report__items th,
    .report__items td {
      text-align: left;
      padding: 4px 16px 4px 0;
      border-bottom: 1px solid #eeeeee;
    }
    .action--create {
      color: #2e7d32;
    }
    .action--update {
      color: #ef6c00;
    }
    .action--delete {
      color: #c62828;
    }
  `,
})
export class ImportReportComponent {
  readonly report = input.required<ImportReport>();
  /** Whether this is the report of removing a starter, not of installing one. */
  readonly uninstall = input(false);
  protected readonly scope = STARTER_IMPORT_I18N_SCOPE;

  /** Where the status and summary words come from; the kinds, actions and columns are shared. */
  protected wordScope(): string {
    return this.uninstall() ? STARTER_UNINSTALL_I18N_SCOPE : this.scope;
  }

  /** From the items rather than `summary`, which the contract leaves optional. */
  protected count(action: ImportAction): number {
    return (this.report().items ?? []).filter((item) => item.action === action).length;
  }
}
