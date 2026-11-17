import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { STARTER_IMPORT_I18N_SCOPE, STARTER_UPGRADE_I18N_SCOPE } from '../base-starter.i18n';
import { UpgradeAction, UpgradeReport } from '../domain/starter';

/** The actions that leave the organization's own definition as it is. */
const KEPT: ReadonlySet<UpgradeAction> = new Set<UpgradeAction>(['keep-customized', 'keep-deleted', 'keep-in-use']);

/**
 * One `UpgradeReport`: from which version to which, what happens to each definition and why some are kept, or why
 * the upgrade was refused. Presentational. The kept definitions are what the organization should look at: the
 * starter's newer version of them did not go in.
 */
@Component({
  selector: 'pp-upgrade-report',
  standalone: true,
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let r = report();
    <section [class]="'report report--' + r.status" data-testid="upgrade-report">
      <header class="report__header">
        <strong data-testid="upgrade-report-status">{{ scope + '.status.' + r.status | transloco }}</strong>
        @if (r.starterId) {
          <span class="report__starter">{{ r.starterId }} {{ r.fromVersion }} → {{ r.toVersion }}</span>
        }
        @if (r.status !== 'rejected') {
          <span data-testid="upgrade-report-summary">{{ scope + '.summary' | transloco: { changed: changed(), kept: kept() } }}</span>
        }
      </header>

      @if (r.errors?.length) {
        <ul class="report__errors" data-testid="upgrade-report-errors">
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

      @if (r.status !== 'rejected') {
        <p class="report__drafts" data-testid="upgrade-report-drafts">{{ scope + '.drafts' | transloco }}</p>
      }
      @if (r.skippedKinds?.length) {
        <p class="report__skipped" data-testid="upgrade-report-skipped">{{ scope + '.skipped' | transloco: { kinds: r.skippedKinds?.join(', ') } }}</p>
      }

      @if (r.items?.length) {
        <table class="report__items" data-testid="upgrade-report-items">
          <thead>
            <tr>
              <th>{{ importScope + '.column.kind' | transloco }}</th>
              <th>{{ importScope + '.column.key' | transloco }}</th>
              <th>{{ importScope + '.column.action' | transloco }}</th>
              <th>{{ scope + '.column.note' | transloco }}</th>
            </tr>
          </thead>
          <tbody>
            @for (item of r.items; track item.kind + ':' + item.key) {
              <tr>
                <td>{{ importScope + '.kind.' + item.kind | transloco }}</td>
                <td><code>{{ item.key }}</code></td>
                <td [class]="'action action--' + item.action">{{ scope + '.action.' + item.action | transloco }}</td>
                <td>{{ item.note ?? '' }}</td>
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
    .report__starter,
    .report__drafts {
      color: #666666;
    }
    .report__drafts,
    .report__skipped {
      margin: 0;
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
    .action--add,
    .action--replace {
      color: #2e7d32;
    }
    .action--remove {
      color: #c62828;
    }
    .action--keep-customized,
    .action--keep-deleted,
    .action--keep-in-use {
      color: #ef6c00;
    }
  `,
})
export class UpgradeReportComponent {
  readonly report = input.required<UpgradeReport>();
  protected readonly scope = STARTER_UPGRADE_I18N_SCOPE;
  protected readonly importScope = STARTER_IMPORT_I18N_SCOPE;

  protected changed(): number {
    return (this.report().items ?? []).filter((item) => !KEPT.has(item.action)).length;
  }

  protected kept(): number {
    return (this.report().items ?? []).filter((item) => KEPT.has(item.action)).length;
  }
}
