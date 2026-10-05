import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { STARTER_IMPORT_I18N_SCOPE } from '../base-starter.i18n';
import { ImportReport } from '../domain/starter';

/** One `ImportReport`: its outcome, the definitions it touched, or why it was refused. Presentational. */
@Component({
  selector: 'pp-import-report',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = report();
    <section class="report" [class]="'report report--' + r.status" data-testid="import-report">
      <header class="report__header">
        <strong data-testid="import-report-status">{{ scope + '.status.' + r.status | transloco }}</strong>
        @if (r.starterId) {
          <span class="report__starter">{{ r.starterId }} {{ r.version }}</span>
        }
        @if (r.status !== 'rejected') {
          <span data-testid="import-report-summary">{{ scope + '.summary' | transloco: { created: created(), updated: updated() } }}</span>
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
  `,
})
export class ImportReportComponent {
  readonly report = input.required<ImportReport>();
  protected readonly scope = STARTER_IMPORT_I18N_SCOPE;

  /** From the items rather than `summary`, which the contract leaves optional. */
  protected readonly created = computed(() => (this.report().items ?? []).filter((item) => item.action === 'create').length);
  protected readonly updated = computed(() => (this.report().items ?? []).filter((item) => item.action === 'update').length);
}
